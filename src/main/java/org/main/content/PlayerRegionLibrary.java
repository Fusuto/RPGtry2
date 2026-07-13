package org.main.content;

import org.main.core.LimbItem;
import org.main.core.LimbSlot;
import org.main.core.PlayerStat;

import java.util.List;
import java.util.Map;

public enum PlayerRegionLibrary {
    MIDLANDS(
            "The Midlands",
            "A hardy human origin with familiar balanced limbs.",
            List.of(
                    MidlandsLimb.head(),
                    MidlandsLimb.body(),
                    MidlandsLimb.leftArm(),
                    MidlandsLimb.rightArm(),
                    MidlandsLimb.legs()
            )
    );

    private final String displayName;
    private final String description;
    private final List<LimbItem> starterLimbs;

    PlayerRegionLibrary(String displayName, String description, List<LimbItem> starterLimbs) {
        this.displayName = displayName;
        this.description = description;
        this.starterLimbs = List.copyOf(starterLimbs);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<LimbItem> createStarterLimbs() {
        return starterLimbs.stream()
                .map(LimbItem::copy)
                .toList();
    }

    private static final class MidlandsLimb {
        private static final String ICON = "assets/images/monster/Nov-2015/player/base/human_m.png";

        private static LimbItem head() {
            return limb("Midlands Head", LimbSlot.HEAD, Map.of());
        }

        private static LimbItem body() {
            return limb("Midlands Body", LimbSlot.BODY, Map.of(PlayerStat.VITALITY, 1, PlayerStat.DEFENSE, 1));
        }

        private static LimbItem leftArm() {
            return limb("Midlands Left Arm", LimbSlot.LEFT_ARM, Map.of(PlayerStat.STRENGTH, 1));
        }

        private static LimbItem rightArm() {
            return limb("Midlands Right Arm", LimbSlot.RIGHT_ARM, Map.of(PlayerStat.STRENGTH, 1));
        }

        private static LimbItem legs() {
            return limb("Midlands Legs", LimbSlot.LEGS, Map.of());
        }

        private static LimbItem limb(String name, LimbSlot slot, Map<PlayerStat, Integer> stats) {
            return new LimbItem(name, null, slot, stats, List.of(), org.main.core.GearDurability.PERFECT, ICON, "", ICON);
        }
    }
}

package org.main.content;

import org.main.core.PlayerStat;

import java.util.List;
import java.util.Map;

public enum PlayerClassLibrary {
    WARRIOR(
            "Warrior",
            "A durable front-line fighter.",
            Map.of(
                    PlayerStat.STRENGTH, 2,
                    PlayerStat.DEFENSE, 1,
                    PlayerStat.VITALITY, 1
            ),
            List.of(SkillLibrary.DEFEND, SkillLibrary.BASH),
            "Knight",
            "Berserker"
    );

    private final String displayName;
    private final String description;
    private final Map<PlayerStat, Integer> preferredStatGrowth;
    private final List<SkillLibrary> starterSkills;
    private final String firstBranchOption;
    private final String secondBranchOption;

    PlayerClassLibrary(
            String displayName,
            String description,
            Map<PlayerStat, Integer> preferredStatGrowth,
            List<SkillLibrary> starterSkills,
            String firstBranchOption,
            String secondBranchOption
    ) {
        this.displayName = displayName;
        this.description = description;
        this.preferredStatGrowth = Map.copyOf(preferredStatGrowth);
        this.starterSkills = List.copyOf(starterSkills);
        this.firstBranchOption = firstBranchOption;
        this.secondBranchOption = secondBranchOption;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Map<PlayerStat, Integer> getPreferredStatGrowth() {
        return preferredStatGrowth;
    }

    public List<SkillLibrary> getStarterSkills() {
        return starterSkills;
    }

    public String getFirstBranchOption() {
        return firstBranchOption;
    }

    public String getSecondBranchOption() {
        return secondBranchOption;
    }
}

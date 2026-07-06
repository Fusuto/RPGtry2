package org.main.battle;

import org.main.engine.EntityType;

public class BattleSkill {
    private final String name;
    private final String description;

    private final SkillTargetShape targetShape;
    private final EntityType targetTeam;
    private final BattleTargetingMode targetingMode;

    public BattleSkill(
            String name,
            String description,
            SkillTargetShape targetShape,
            EntityType targetTeam,
            BattleTargetingMode targetingMode
    ) {
        this.name = name;
        this.description = description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
    }

    public BattleSkill(
            String name,
            SkillTargetShape targetShape,
            EntityType targetTeam,
            BattleTargetingMode targetingMode
    ) {
        this(name, "", targetShape, targetTeam, targetingMode);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillTargetShape getTargetShape() {
        return targetShape;
    }

    public EntityType getTargetTeam() {
        return targetTeam;
    }

    public BattleTargetingMode getTargetingMode() {
        return targetingMode;
    }
}
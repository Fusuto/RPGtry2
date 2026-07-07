package org.main.content;

import org.main.core.Library;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

public enum NpcLibrary {
    OLD_GUARD(
            MonsterType.SKELETON,
            "old_guard_intro"
    ),

    GOBLIN_MERCHANT(
            MonsterType.GOBLIN,
            "merchant_basic"
    );

    private final MonsterType monsterType;
    private final String interactionId;

    NpcLibrary(MonsterType monsterType, String interactionId) {
        this.monsterType = monsterType;
        this.interactionId = interactionId;
    }

    public MapEntity createEntity(int x, int y) {
        MapEntity entity = new MapEntity(
                new Monster(monsterType),
                x,
                y,
                Library.EntityType.NPC
        );

        if (interactionId != null && !interactionId.isBlank()) {
            entity.withInteractionId(interactionId);
        }

        return entity;
    }

    public MonsterType getMonsterType() {
        return monsterType;
    }

    public String getInteractionId() {
        return interactionId;
    }
}

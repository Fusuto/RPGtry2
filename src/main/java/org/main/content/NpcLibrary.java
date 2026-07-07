package org.main.content;

import org.main.core.Library;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

public enum NpcLibrary {
    OLD_GUARD(
            MonsterType.SKELETON,
            "old_guard_intro",
            "src/main/java/org/main/sounds/generated/skelle_talk.wav"
    ),

    GOBLIN_MERCHANT(
            MonsterType.GOBLIN,
            "merchant_basic",
            "src/main/java/org/main/sounds/generated/gobbo_talk.wav"
    );

    private final MonsterType monsterType;
    private final String interactionId;
    private final String talkSoundPath;

    NpcLibrary(MonsterType monsterType, String interactionId, String talkSoundPath) {
        this.monsterType = monsterType;
        this.interactionId = interactionId;
        this.talkSoundPath = talkSoundPath;
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

        if (talkSoundPath != null && !talkSoundPath.isBlank()) {
            entity.withTalkSoundPath(talkSoundPath);
        }

        return entity;
    }

    public MonsterType getMonsterType() {
        return monsterType;
    }

    public String getInteractionId() {
        return interactionId;
    }

    public String getTalkSoundPath() {
        return talkSoundPath;
    }
}

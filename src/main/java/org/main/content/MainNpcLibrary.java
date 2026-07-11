package org.main.content;

import org.main.core.Library;
import org.main.engine.MapEntity;
import org.main.monsters.MonsterType;

public enum MainNpcLibrary {
    TIPPING_THE_HAT_SKELETON(
            "Sir Tibia",
            MonsterType.SKELETON,
            "old_guard_intro",
            "assets/sounds/generated/skelle_talk.wav"
    );

    private final String displayName;
    private final MonsterType visualType;
    private final String interactionId;
    private final String talkSoundPath;

    MainNpcLibrary(String displayName, MonsterType visualType, String interactionId, String talkSoundPath) {
        this.displayName = displayName;
        this.visualType = visualType;
        this.interactionId = interactionId;
        this.talkSoundPath = talkSoundPath;
    }

    public MapEntity createEntity(int x, int y) {
        return new MapEntity(displayName, Library.EntityType.NPC, x, y, visualType.getImg())
                .withInteractionId(interactionId)
                .withTalkSoundPath(talkSoundPath);
    }
}

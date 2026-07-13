package org.main.content;

import org.main.core.Library;
import org.main.engine.AssetLoader;
import org.main.engine.MapEntity;

public enum MainNpcLibrary {
    TIPPING_THE_HAT_SKELETON(
            "Sir Tibia",
            "assets/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png",
            "old_guard_intro",
            "assets/sounds/generated/skelle_talk.wav"
    );

    private final String displayName;
    private final String imagePath;
    private final String interactionId;
    private final String talkSoundPath;

    MainNpcLibrary(String displayName, String imagePath, String interactionId, String talkSoundPath) {
        this.displayName = displayName;
        this.imagePath = imagePath;
        this.interactionId = interactionId;
        this.talkSoundPath = talkSoundPath;
    }

    public MapEntity createEntity(int x, int y) {
        return new MapEntity(displayName, Library.EntityType.NPC, x, y, AssetLoader.loadImage(imagePath))
                .withInteractionId(interactionId)
                .withTalkSoundPath(talkSoundPath);
    }
}

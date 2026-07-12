package org.main.content;

import org.main.engine.EnvironmentTheme;

import java.util.List;

public enum EnvironmentLibrary {
    STARTER_DUNGEON(
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "brick", "stone", "center"),
                    EnvironmentTheme.texture("door", "wood", "handle", "center"),
                    EnvironmentTheme.texture("floor", "wood", "planks", "wide")
            ),
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "brick", "sand", "center"),
                    EnvironmentTheme.texture("door", "metal", "gate", "lock"),
                    EnvironmentTheme.texture("floor", "tiles", "sand", "small")
            ),
            "assets/sounds/generated/Bryan stuff 11.wav",
            "assets/sounds/generated/Concept 4.wav",
            "assets/sounds/generated/footstep.wav",
            "assets/sounds/generated/player_hit.wav"
    ),

    ;

    private final List<EnvironmentTheme> themes;
    private final String ambienceSoundPath;
    private final String combatMusicPath;
    private final String footstepSoundPath;
    private final String playerHitSoundPath;

    EnvironmentLibrary(
            EnvironmentTheme primaryTheme,
            EnvironmentTheme alternateTheme,
            String ambienceSoundPath,
            String combatMusicPath,
            String footstepSoundPath,
            String playerHitSoundPath
    ) {
        this.themes = List.of(primaryTheme, alternateTheme);
        this.ambienceSoundPath = ambienceSoundPath;
        this.combatMusicPath = combatMusicPath;
        this.footstepSoundPath = footstepSoundPath;
        this.playerHitSoundPath = playerHitSoundPath;
    }

    public List<EnvironmentTheme> getThemes() {
        return themes;
    }

    public String getAmbienceSoundPath() {
        return ambienceSoundPath;
    }

    public String getCombatMusicPath() {
        return combatMusicPath;
    }

    public String getFootstepSoundPath() {
        return footstepSoundPath;
    }

    public String getPlayerHitSoundPath() {
        return playerHitSoundPath;
    }
}

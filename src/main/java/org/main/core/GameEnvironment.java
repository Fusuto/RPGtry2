package org.main.core;

import org.main.content.ThemeLibrary;
import org.main.engine.EnvironmentTheme;

import java.util.List;

public final class GameEnvironment {

    private final List<EnvironmentTheme> themes;
    private final List<ThemeLibrary> themeLibraries;
    private final String ambienceSoundPath;
    private final String combatMusicPath;
    private final String footstepSoundPath;
    private final String playerHitSoundPath;

    private GameEnvironment(
            ThemeLibrary primaryTheme,
            ThemeLibrary alternateTheme,
            String ambienceSoundPath,
            String combatMusicPath,
            String footstepSoundPath,
            String playerHitSoundPath
    ) {
        this.themeLibraries = List.of(primaryTheme, alternateTheme);
        this.themes = themeLibraries.stream()
                .map(ThemeLibrary::getTheme)
                .toList();
        this.ambienceSoundPath = ambienceSoundPath;
        this.combatMusicPath = combatMusicPath;
        this.footstepSoundPath = footstepSoundPath;
        this.playerHitSoundPath = playerHitSoundPath;
    }

    public static GameEnvironment createDefault() {
        return new GameEnvironment(
                ThemeLibrary.STONE_WOOD,
                ThemeLibrary.SANDSTONE_GATE,
                "assets/sounds/generated/Bryan stuff 11.wav",
                "assets/sounds/generated/Concept 4.wav",
                "assets/sounds/generated/footstep.wav",
                "assets/sounds/generated/player_hit.wav"
        );
    }

    public List<EnvironmentTheme> getThemes() {
        return themes;
    }

    public List<ThemeLibrary> getThemeLibraries() {
        return themeLibraries;
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

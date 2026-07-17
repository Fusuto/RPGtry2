package org.main.content;

import org.main.engine.EnvironmentTheme;

public enum ThemeLibrary {
    STONE_WOOD(
            "Stone and Wood",
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "brick", "stone", "center"),
                    EnvironmentTheme.texture("door", "wood", "handle", "center"),
                    EnvironmentTheme.texture("floor", "wood", "planks", "wide")
            )
    ),

    SANDSTONE_GATE(
            "Sandstone Gate",
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "brick", "sand", "center"),
                    EnvironmentTheme.texture("door", "metal", "gate", "lock"),
                    EnvironmentTheme.texture("floor", "tiles", "sand", "small")
            )
    ),

    OUTDOOR_HOUSE_GRASS(
            "Outdoor House Grass",
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "timber", "structure", "horizontal"),
                    EnvironmentTheme.texture("door", "wood", "handle", "center"),
                    EnvironmentTheme.texture("floor", "ground", "nature", "grass")
            )
    ),

    OUTDOOR_HOUSE_DIRT(
            "Outdoor House Dirt",
            EnvironmentTheme.of(
                    EnvironmentTheme.texture("wall", "timber", "structure", "cross"),
                    EnvironmentTheme.texture("door", "wood", "handle", "center"),
                    EnvironmentTheme.texture("floor", "ground", "nature", "dirt")
            )
    );

    private final String displayName;
    private final EnvironmentTheme theme;

    ThemeLibrary(String displayName, EnvironmentTheme theme) {
        this.displayName = displayName;
        this.theme = theme;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EnvironmentTheme getTheme() {
        return theme;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

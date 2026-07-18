package org.main.engine;

public record EnvironmentTheme(
        TextureTheme wall,
        TextureTheme door,
        TextureTheme floor,
        TextureTheme roof
) {
    public record TextureTheme(
            String location,
            String material1,
            String material2,
            String side
    ) {
    }

    public static EnvironmentTheme of(
            TextureTheme wall,
            TextureTheme door,
            TextureTheme floor,
            TextureTheme roof
    ) {
        return new EnvironmentTheme(wall, door, floor, roof);
    }

    public static TextureTheme texture(
            String location,
            String material1,
            String material2,
            String side
    ) {
        return new TextureTheme(location, material1, material2, side);
    }

    public static EnvironmentTheme defaultTheme() {
        return of(
                texture("wall", "brick", "stone", "center"),
                texture("door", "wood", "handle", "center"),
                texture("floor", "wood", "planks", "wide"),
                texture("roof", "clay", "red", "center")
        );
    }
}

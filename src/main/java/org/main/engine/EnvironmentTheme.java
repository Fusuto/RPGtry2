package org.main.engine;

public record EnvironmentTheme(
        TextureTheme wall,
        TextureTheme door,
        TextureTheme floor
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
            TextureTheme floor
    ) {
        return new EnvironmentTheme(wall, door, floor);
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
                texture("floor", "wood", "planks", "wide")
        );
    }

    public EnvironmentTheme withWall(String location, String material1, String material2) {
        return new EnvironmentTheme(
                texture(location, material1, material2, "center"),
                door,
                floor
        );
    }

    public EnvironmentTheme withDoor(String location, String material1, String material2, String side) {
        return new EnvironmentTheme(
                wall,
                texture(location, material1, material2, side),
                floor
        );
    }

    public EnvironmentTheme withFloor(String location, String material1, String material2, String floorType) {
        return new EnvironmentTheme(
                wall,
                door,
                texture(location, material1, material2, floorType)
        );
    }
}

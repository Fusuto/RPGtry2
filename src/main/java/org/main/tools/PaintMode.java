package org.main.tools;

enum PaintMode {
    TILE("Tile"),
    FLOOR_BRUSH("Floor Brush"),
    WALL_BRUSH("Wall Brush"),
    DOOR_BRUSH("Door Brush"),
    ROOF_BRUSH("Roof Brush"),
    MOB_AREA("Mob Area"),
    CLEAR_MOB_AREA("Clear Mob Area"),
    CLEAR_BRUSH("Clear Brushes"),
    SET_HEIGHT("Set Height"),
    PLACE_OBJECT("Place Object"),
    ERASE_OBJECT("Erase Object"),
    SET_SPAWN("Set Spawn"),
    PLACE_LIGHT("Place Light"),
    PLACE_TRIGGER("Place Trigger"),
    WIRE_TRIGGER("Wire Trigger"),
    PLACE_PREFAB("Place Prefab");

    private final String label;

    PaintMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}


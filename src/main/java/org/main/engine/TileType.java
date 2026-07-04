package org.main.engine;

public enum TileType {
    FLOOR(false),
    WALL(true),
    DOOR_CLOSED(true),
    DOOR_OPEN(false),
    TRAP(false),
    STAIRS_DOWN(false),
    STAIRS_UP(false);

    private final boolean blocksMovement;

    TileType(boolean blocksMovement) {
        this.blocksMovement = blocksMovement;
    }

    public boolean blocksMovement() {
        return blocksMovement;
    }

    public boolean isWallLike() {
        return this == WALL || this == DOOR_CLOSED;
    }

    public boolean isDoor() {
        return this == DOOR_CLOSED || this == DOOR_OPEN;
    }
}
package org.main.engine;

public class MapEntity {
    private final String name;
    private final EntityType type;

    private int x;
    private int y;

    public MapEntity(String name, EntityType type, int x, int y) {
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public EntityType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isAt(int x, int y) {
        return this.x == x && this.y == y;
    }

    public boolean blocksMovement() {
        return type == EntityType.ENEMY
                || type == EntityType.ALLY
                || type == EntityType.NPC
                || type == EntityType.CHEST;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
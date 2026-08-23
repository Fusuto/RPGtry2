package org.main.engine;

/**
 * One authoritative mapping for the game's 0=N, 1=E, 2=S, 3=W convention.
 */
public enum GridDirection {
    NORTH(0, 0, -1, 1, 0, 0.0),
    EAST(1, 1, 0, 0, 1, -90.0),
    SOUTH(2, 0, 1, -1, 0, 180.0),
    WEST(3, -1, 0, 0, -1, 90.0);

    private static final GridDirection[] INDEXED = values();

    private final int index;
    private final int forwardX;
    private final int forwardY;
    private final int rightX;
    private final int rightY;
    private final double yawDegrees;

    GridDirection(int index, int forwardX, int forwardY, int rightX, int rightY, double yawDegrees) {
        this.index = index;
        this.forwardX = forwardX;
        this.forwardY = forwardY;
        this.rightX = rightX;
        this.rightY = rightY;
        this.yawDegrees = yawDegrees;
    }

    public static GridDirection fromIndex(int direction) {
        return INDEXED[Math.floorMod(direction, INDEXED.length)];
    }

    public static int forwardX(int direction) {
        return fromIndex(direction).forwardX;
    }

    public static int forwardY(int direction) {
        return fromIndex(direction).forwardY;
    }

    public static int rightX(int direction) {
        return fromIndex(direction).rightX;
    }

    public static int rightY(int direction) {
        return fromIndex(direction).rightY;
    }

    public static double yawDegrees(int direction) {
        return fromIndex(direction).yawDegrees;
    }

    public int index() {
        return index;
    }

    public int forwardX() {
        return forwardX;
    }

    public int forwardY() {
        return forwardY;
    }

    public int rightX() {
        return rightX;
    }

    public int rightY() {
        return rightY;
    }

    public double yawDegrees() {
        return yawDegrees;
    }

    public GridDirection turnRight() {
        return fromIndex(index + 1);
    }

    public GridDirection turnLeft() {
        return fromIndex(index - 1);
    }
}

package org.main.core;

public enum LimbSlot {
    HEAD("Head"),
    BODY("Body"),
    LEFT_ARM("Left Arm"),
    RIGHT_ARM("Right Arm"),
    LEGS("Legs");

    private final String displayName;

    LimbSlot(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }
}

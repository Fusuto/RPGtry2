package org.main.core;

public class NavigationState {
    private int playerX = 1;
    private int playerY = 1;
    private int direction = 1; // 0 = north, 1 = east, 2 = south, 3 = west
    private double movementStartX = 1.0;
    private double movementStartY = 1.0;
    private double movementProgress = 1.0;
    private double rotationStartOffsetRadians = 0.0;
    private double rotationProgress = 1.0;
    private GameState.CameraMovementMode cameraMovementMode = GameState.CameraMovementMode.FLUID;

    public int getPlayerX() {
        return playerX;
    }

    public void setPlayerX(int playerX) {
        this.playerX = playerX;
    }

    public int getPlayerY() {
        return playerY;
    }

    public void setPlayerY(int playerY) {
        this.playerY = playerY;
    }

    public void setPlayerPosition(int x, int y) {
        this.playerX = x;
        this.playerY = y;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = Math.floorMod(direction, 4);
    }

    public double getMovementStartX() {
        return movementStartX;
    }

    public void setMovementStartX(double movementStartX) {
        this.movementStartX = movementStartX;
    }

    public double getMovementStartY() {
        return movementStartY;
    }

    public void setMovementStartY(double movementStartY) {
        this.movementStartY = movementStartY;
    }

    public double getMovementProgress() {
        return movementProgress;
    }

    public void setMovementProgress(double movementProgress) {
        this.movementProgress = Math.max(0.0, Math.min(1.0, movementProgress));
    }

    public double getRotationStartOffsetRadians() {
        return rotationStartOffsetRadians;
    }

    public void setRotationStartOffsetRadians(double rotationStartOffsetRadians) {
        this.rotationStartOffsetRadians = rotationStartOffsetRadians;
    }

    public double getRotationProgress() {
        return rotationProgress;
    }

    public void setRotationProgress(double rotationProgress) {
        this.rotationProgress = Math.max(0.0, Math.min(1.0, rotationProgress));
    }

    public GameState.CameraMovementMode getCameraMovementMode() {
        return cameraMovementMode;
    }

    public void setCameraMovementMode(GameState.CameraMovementMode cameraMovementMode) {
        if (cameraMovementMode != null) {
            this.cameraMovementMode = cameraMovementMode;
        }
    }
}

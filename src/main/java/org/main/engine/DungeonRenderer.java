package org.main.engine;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DungeonRenderer {
    private static final int TILE_WALL = 1;

    private static final int MAX_DEPTH = 5;
    private static final int SIDE_MARGIN = 1;

    private static final double HORIZONTAL_FOCAL_RATIO = 0.55;
    private static final double WALL_HALF_HEIGHT_RATIO = 0.48;
    private static final double NEAR_CLIP = 0.10;


    private TextureManager textureManager;

    private String wallLocation = "wall";
    private String wallMaterial1 = "brick";
    private String wallMaterial2 = "stone";

    // Future image tile support
    private Image wallTexture = null;
    private Image floorTexture = null;


    private DungeonMap map;
    private int playerX;
    private int playerY;
    private int dir;
    private int viewWidth;
    private int viewHeight;


    private enum FaceType {
        FRONT,
        LEFT,
        RIGHT,
        FLOOR
    }

    private static class RenderCommand {
        FaceType faceType;
        TileType tileType;
        int depth;
        int side;
        Polygon polygon;
        double sortZ;

        RenderCommand(FaceType faceType, TileType tileType, int depth, int side, Polygon polygon, double sortZ) {
            this.faceType = faceType;
            this.tileType = tileType;
            this.depth = depth;
            this.side = side;
            this.polygon = polygon;
            this.sortZ = sortZ;
        }
    }

    public void setTextureManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public void setWallTextureTheme(String location, String material1, String material2) {
        this.wallLocation = location;
        this.wallMaterial1 = material1;
        this.wallMaterial2 = material2;
    }

    public void draw(
            Graphics2D g,
            DungeonMap map,
            int playerX,
            int playerY,
            int dir,
            int viewWidth,
            int viewHeight
    ) {
        this.map = map;
        this.playerX = playerX;
        this.playerY = playerY;
        this.dir = dir;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;

        drawDungeonBackground(g);

        List<RenderCommand> floorCommands = buildFloorRenderCommands();
        floorCommands.sort(Comparator.comparingDouble((RenderCommand c) -> c.sortZ).reversed());

        for (RenderCommand command : floorCommands) {
            drawRenderCommand(g, command);
        }

        List<RenderCommand> wallCommands = buildWallRenderCommands();
        wallCommands.sort(Comparator.comparingDouble((RenderCommand c) -> c.sortZ).reversed());

        for (RenderCommand command : wallCommands) {
            drawRenderCommand(g, command);
        }
    }

    private List<RenderCommand> buildFloorRenderCommands() {
        List<RenderCommand> commands = new ArrayList<>();

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            int sideLimit = depth + SIDE_MARGIN;

            for (int side = -sideLimit; side <= sideLimit; side++) {
                TileType tileType = getTileAtRelative(depth, side);

                if (tileType.isWallLike()) {
                    continue;
                }

                commands.add(createFloorFace(depth, side, tileType));
            }
        }

        return commands;
    }

    private RenderCommand createFloorFace(int depth, int side, TileType tileType) {
        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;

        double leftX = side - 0.5;
        double rightX = side + 0.5;

        Polygon polygon = polygonFrom(
                project(leftX, nearZ, -1.0),
                project(rightX, nearZ, -1.0),
                project(rightX, farZ, -1.0),
                project(leftX, farZ, -1.0)
        );

        double sortZ = (nearZ + farZ) / 2.0;

        return new RenderCommand(FaceType.FLOOR, tileType, depth, side, polygon, sortZ);
    }

    private void drawDungeonBackground(Graphics2D g) {
        g.setColor(new Color(10, 10, 15));
        g.fillRect(0, 0, viewWidth, viewHeight);

        g.setColor(new Color(20, 20, 30));
        g.fillRect(0, 0, viewWidth, viewHeight / 2);

        g.setColor(new Color(25, 20, 15));
        g.fillRect(0, viewHeight / 2, viewWidth, viewHeight / 2);

        g.setColor(new Color(35, 35, 45));
        g.drawLine(0, viewHeight / 2, viewWidth, viewHeight / 2);
    }

    private List<RenderCommand> buildWallRenderCommands() {
        List<RenderCommand> commands = new ArrayList<>();

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            int sideLimit = depth + SIDE_MARGIN;

            for (int side = -sideLimit; side <= sideLimit; side++) {
                TileType tileType = getTileAtRelative(depth, side);

                if (!tileType.isWallLike()) {
                    continue;
                }

                if (!isWallAtRelative(depth - 1, side)) {
                    commands.add(createFrontFace(depth, side, tileType));
                }

                if (!isWallAtRelative(depth, side - 1)) {
                    commands.add(createLeftFace(depth, side, tileType));
                }

                if (!isWallAtRelative(depth, side + 1)) {
                    commands.add(createRightFace(depth, side, tileType));
                }
            }
        }

        return commands;
    }

    private TileType getTileAtRelative(int forward, int side) {
        int x = playerX
                + forwardX() * forward
                + rightX() * side;

        int y = playerY
                + forwardY() * forward
                + rightY() * side;

        return map.getTile(x, y);
    }

    private RenderCommand createFrontFace(int depth, int side, TileType tileType) {
        double z = depth - 0.5;

        double leftX = side - 0.5;
        double rightX = side + 0.5;

        Polygon polygon = polygonFrom(
                project(leftX, z, 1.0),
                project(rightX, z, 1.0),
                project(rightX, z, -1.0),
                project(leftX, z, -1.0)
        );

        return new RenderCommand(FaceType.FRONT, tileType, depth, side, polygon, z);
    }

    private RenderCommand createLeftFace(int depth, int side, TileType tileType) {
        double x = side - 0.5;

        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;

        /*
         * If this wall is immediately to the player's right,
         * its left face should stretch very close to the camera.
         * This prevents fullscreen "peeking" around the nearest wall.
         */
        if (depth == 1 && side > 0) {
            nearZ = NEAR_CLIP;
        }

        Polygon polygon = polygonFrom(
                project(x, nearZ, 1.0),
                project(x, farZ, 1.0),
                project(x, farZ, -1.0),
                project(x, nearZ, -1.0)
        );

        double sortZ = (nearZ + farZ) / 2.0;

        return new RenderCommand(FaceType.LEFT, tileType, depth, side, polygon, sortZ);
    }

    private RenderCommand createRightFace(int depth, int side, TileType tileType) {
        double x = side + 0.5;

        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;

        /*
         * If this wall is immediately to the player's left,
         * its right face should stretch very close to the camera.
         */
        if (depth == 1 && side < 0) {
            nearZ = NEAR_CLIP;
        }

        Polygon polygon = polygonFrom(
                project(x, nearZ, 1.0),
                project(x, farZ, 1.0),
                project(x, farZ, -1.0),
                project(x, nearZ, -1.0)
        );

        double sortZ = (nearZ + farZ) / 2.0;

        return new RenderCommand(FaceType.RIGHT, tileType, depth, side, polygon, sortZ);
    }

    private Point project(double x, double z, double vertical) {
        z = Math.max(z, NEAR_CLIP);

        int centerX = viewWidth / 2;
        int horizonY = viewHeight / 2;

        double focalLength = getFocalLength();
        double wallHalfHeight = getWallHalfHeight();

        int screenX = centerX + (int) Math.round((x / z) * focalLength);
        int screenY = horizonY - (int) Math.round((vertical / z) * wallHalfHeight);

        return new Point(screenX, screenY);
    }

    private double getFocalLength() {
        return viewWidth * HORIZONTAL_FOCAL_RATIO;
    }

    private double getWallHalfHeight() {
        return viewHeight * WALL_HALF_HEIGHT_RATIO;
    }

    private Polygon polygonFrom(Point... points) {
        Polygon polygon = new Polygon();

        for (Point point : points) {
            polygon.addPoint(point.x, point.y);
        }

        return polygon;
    }

    private void drawRenderCommand(Graphics2D g, RenderCommand command) {
        Color color = getRenderColor(command);

        Image texture = switch (command.faceType) {
            case FLOOR -> floorTexture;
            default -> wallTexture;
        };

        if (texture != null) {
            Shape oldClip = g.getClip();

            Rectangle bounds = command.polygon.getBounds();

            g.setClip(command.polygon);
            g.drawImage(
                    texture,
                    bounds.x,
                    bounds.y,
                    Math.max(1, bounds.width),
                    Math.max(1, bounds.height),
                    null
            );

            g.setClip(oldClip);
        } else {
            g.setColor(color);
            g.fillPolygon(command.polygon);
        }

        g.setColor(Color.BLACK);
        g.drawPolygon(command.polygon);
    }

    private Color getRenderColor(RenderCommand command) {
        if (command.faceType == FaceType.FLOOR) {
            int base = switch (command.depth) {
                case 1 -> 65;
                case 2 -> 55;
                case 3 -> 45;
                case 4 -> 35;
                default -> 25;
            };

            return new Color(base, base - 10, base - 20);
        }

        // Existing wall/door color logic goes here.
        return getWallColor(command);
    }

    private Color getWallColor(RenderCommand command) {
        if (command.tileType == TileType.DOOR_CLOSED) {
            int base = switch (command.depth) {
                case 1 -> 110;
                case 2 -> 85;
                case 3 -> 65;
                case 4 -> 50;
                default -> 35;
            };

            return switch (command.faceType) {
                case FRONT -> new Color(base + 25, base - 15, base - 35);
                case LEFT -> new Color(base, base - 25, base - 40);
                case RIGHT -> new Color(base + 10, base - 20, base - 35);
                case FLOOR -> null;
            };
        }

        int base = switch (command.depth) {
            case 1 -> 115;
            case 2 -> 90;
            case 3 -> 70;
            case 4 -> 55;
            default -> 40;
        };

        return switch (command.faceType) {
            case FRONT -> new Color(base, base, base + 15);
            case LEFT -> new Color(Math.max(0, base - 20), Math.max(0, base - 20), base);
            case RIGHT -> new Color(Math.max(0, base - 10), Math.max(0, base - 10), base + 5);
            case FLOOR -> null;
        };
    }

    public void setFloorTexture(Image floorTexture) {
        this.floorTexture = floorTexture;
    }

    private boolean isWallAtRelative(int forward, int side) {
        int x = playerX
                + forwardX() * forward
                + rightX() * side;

        int y = playerY
                + forwardY() * forward
                + rightY() * side;

        return isWall(x, y);
    }

    private boolean isWall(int x, int y) {
        return map.isWallLike(x, y);
    }

    private int forwardX() {
        return switch (dir) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private int forwardY() {
        return switch (dir) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }

    private int leftX() {
        return switch (dir) {
            case 0 -> -1;
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 0;
            default -> 0;
        };
    }

    private int leftY() {
        return switch (dir) {
            case 0 -> 0;
            case 1 -> -1;
            case 2 -> 0;
            case 3 -> 1;
            default -> 0;
        };
    }

    private int rightX() {
        return -leftX();
    }

    private int rightY() {
        return -leftY();
    }
}
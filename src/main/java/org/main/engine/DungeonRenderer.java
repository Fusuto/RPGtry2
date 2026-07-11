package org.main.engine;

import org.main.core.Library;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DungeonRenderer {
    private static final int MAX_DEPTH = 5;
    private static final int SIDE_MARGIN = 1;
    private static final double HORIZONTAL_FOCAL_RATIO = 0.55;
    private static final double WALL_HALF_HEIGHT_RATIO = 0.48;
    private static final double NEAR_CLIP = 0.10;
    private static final int MAX_TEXTURE_STRIPS = 48;
    private static final int MIN_TEXTURE_STRIPS = 8;
    private static final String WATER_PATH = "assets/images/monster/Nov-2015/dngn/water/";
    private final BufferedImage[] decorativeWaterFrames = loadNumberedFrames("shoals_shallow_water", 0, 11);
    private final BufferedImage[] fishingWaterFrames = {
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance1.png"),
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance2.png"),
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance3.png")
    };
    private TextureManager textureManager;
    private List<EnvironmentTheme> environmentThemes = new ArrayList<>(
            List.of(EnvironmentTheme.defaultTheme())
    );

    private static final double DOOR_OVERLAY_LEFT = 0.23;
    private static final double DOOR_OVERLAY_RIGHT = 0.77;
    private static final double DOOR_OVERLAY_TOP = 0.16;
    private static final double DOOR_OVERLAY_BOTTOM = 0.98;
    private DungeonMap map;
    private int playerX;
    private int playerY;
    private int dir;
    private int viewWidth;
    private int viewHeight;
    private double cameraOffsetForward;
    private double cameraOffsetSide;
    private double cameraRotationRadians;

    private enum FaceType {FRONT, LEFT, RIGHT, FLOOR, SPRITE}

    private static class RenderCommand {
        FaceType faceType;
        Library.TileType tileType;
        int depth;
        int side;
        int worldX;
        int worldY;
        Polygon polygon;
        double sortZ;
        BufferedImage spriteImage;

        RenderCommand(FaceType faceType, Library.TileType tileType, int depth, int side, int worldX, int worldY, Polygon polygon, double sortZ) {
            this.faceType = faceType;
            this.tileType = tileType;
            this.depth = depth;
            this.side = side;
            this.worldX = worldX;
            this.worldY = worldY;
            this.polygon = polygon;
            this.sortZ = sortZ;
        }

        RenderCommand(BufferedImage spriteImage, int depth, int side, int worldX, int worldY, Polygon polygon, double sortZ) {
            this.faceType = FaceType.SPRITE;
            this.spriteImage = spriteImage;
            this.depth = depth;
            this.side = side;
            this.worldX = worldX;
            this.worldY = worldY;
            this.polygon = polygon;
            this.sortZ = sortZ;
        }
    }

    private static class RelativePosition {
        int forward;
        int side;

        RelativePosition(int forward, int side) {
            this.forward = forward;
            this.side = side;
        }
    }

    public void setTextureManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public void setEnvironmentThemes(List<EnvironmentTheme> environmentThemes) {
        if (environmentThemes == null || environmentThemes.isEmpty()) {
            this.environmentThemes = new ArrayList<>(List.of(EnvironmentTheme.defaultTheme()));
            return;
        }

        this.environmentThemes = new ArrayList<>(environmentThemes);
    }

    public void setWallTextureTheme(String location, String material1, String material2) {
        setPrimaryEnvironmentTheme(getPrimaryEnvironmentTheme().withWall(location, material1, material2));
    }

    public void setDoorTextureTheme(String location, String material1, String material2) {
        setDoorTextureTheme(location, material1, material2, "center");
    }

    public void setDoorTextureTheme(String location, String material1, String material2, String side) {
        setPrimaryEnvironmentTheme(getPrimaryEnvironmentTheme().withDoor(location, material1, material2, side));
    }

    public void setFloorTextureTheme(String location, String material1, String material2, String floorType) {
        setPrimaryEnvironmentTheme(getPrimaryEnvironmentTheme().withFloor(location, material1, material2, floorType));
    }

    private EnvironmentTheme getPrimaryEnvironmentTheme() {
        return getEnvironmentTheme(0);
    }

    private void setPrimaryEnvironmentTheme(EnvironmentTheme environmentTheme) {
        if (environmentThemes.isEmpty()) {
            environmentThemes.add(environmentTheme);
            return;
        }

        environmentThemes.set(0, environmentTheme);
    }

    public void draw(Graphics2D g, DungeonMap map, List<MapEntity> entities, int playerX, int playerY, int dir, int viewWidth, int viewHeight) {
        draw(g, map, entities, playerX, playerY, dir, viewWidth, viewHeight, 0.0, 0.0, 0.0);
    }

    public void draw(
            Graphics2D g,
            DungeonMap map,
            List<MapEntity> entities,
            int playerX,
            int playerY,
            int dir,
            int viewWidth,
            int viewHeight,
            double cameraOffsetForward,
            double cameraOffsetSide,
            double cameraRotationRadians
    ) {
        this.map = map;
        this.playerX = playerX;
        this.playerY = playerY;
        this.dir = dir;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.cameraOffsetForward = cameraOffsetForward;
        this.cameraOffsetSide = cameraOffsetSide;
        this.cameraRotationRadians = cameraRotationRadians;
        drawDungeonBackground(g); /* * Draw floors first so they never accidentally render over walls. */
        List<RenderCommand> floorCommands = buildFloorRenderCommands();
        floorCommands.sort(Comparator.comparingDouble((RenderCommand c) -> c.sortZ).reversed());
        for (RenderCommand command : floorCommands) {
            drawRenderCommand(g, command);
        } /* * Draw walls and entities together so sprites can be depth-sorted * against wall faces. */
        List<RenderCommand> sceneCommands = buildWallRenderCommands();
        sceneCommands.addAll(buildEntityRenderCommands(entities));
        sceneCommands.sort(Comparator.comparingDouble((RenderCommand c) -> c.sortZ).reversed());
        for (RenderCommand command : sceneCommands) {
            drawRenderCommand(g, command);
        }
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

    private List<RenderCommand> buildFloorRenderCommands() {
        List<RenderCommand> commands = new ArrayList<>();
        for (int depth = getMinimumRenderDepth(); depth <= MAX_DEPTH; depth++) {
            int sideLimit = getSideRenderLimit(depth);
            for (int side = -sideLimit; side <= sideLimit; side++) {
                Library.TileType tileType = getTileAtRelative(depth, side);
                if (tileType.isWallLike()) {
                    continue;
                }
                RenderCommand command = createFloorFace(depth, side, tileType);
                if (command != null) {
                    commands.add(command);
                }
            }
        }
        return commands;
    }

    private RenderCommand createFloorFace(int depth, int side, Library.TileType tileType) {
        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;
        double leftX = side - 0.5;
        double rightX = side + 0.5;
        if (shouldCullFace(leftX, nearZ, rightX, nearZ, rightX, farZ, leftX, farZ)) {
            return null;
        }
        Point worldPoint = worldPointAtRelative(depth, side);
        Polygon polygon = polygonFrom(project(leftX, nearZ, -1.0), project(rightX, nearZ, -1.0), project(rightX, farZ, -1.0), project(leftX, farZ, -1.0));
        double sortZ = (nearZ + farZ) / 2.0;
        return new RenderCommand(FaceType.FLOOR, tileType, depth, side, worldPoint.x, worldPoint.y, polygon, sortZ);
    }

    private List<RenderCommand> buildWallRenderCommands() {
        List<RenderCommand> commands = new ArrayList<>();
        for (int depth = getMinimumRenderDepth(); depth <= MAX_DEPTH; depth++) {
            int sideLimit = getSideRenderLimit(depth);
            for (int side = -sideLimit; side <= sideLimit; side++) {
                Point worldPoint = worldPointAtRelative(depth, side);
                Library.TileType tileType = map.getTile(worldPoint.x, worldPoint.y);
                if (!tileType.isWallLike()) {
                    continue;
                }
                if (depth > 0 && !isWallAtRelative(depth - 1, side)) {
                    RenderCommand command = createFrontFace(depth, side, worldPoint.x, worldPoint.y, tileType);
                    if (command != null) {
                        commands.add(command);
                    }
                }
                if (!isWallAtRelative(depth, side - 1)) {
                    RenderCommand command = createLeftFace(depth, side, worldPoint.x, worldPoint.y, tileType);
                    if (command != null) {
                        commands.add(command);
                    }
                }
                if (!isWallAtRelative(depth, side + 1)) {
                    RenderCommand command = createRightFace(depth, side, worldPoint.x, worldPoint.y, tileType);
                    if (command != null) {
                        commands.add(command);
                    }
                }
            }
        }
        return commands;
    }

    private int getMinimumRenderDepth() {
        return isCameraTransitionActive() ? 0 : 1;
    }

    private int getSideRenderLimit(int depth) {
        if (isCameraRotationActive()) {
            return MAX_DEPTH + SIDE_MARGIN;
        }

        return depth + SIDE_MARGIN;
    }

    private boolean isCameraTransitionActive() {
        return Math.abs(cameraOffsetForward) > 0.001
                || Math.abs(cameraOffsetSide) > 0.001
                || isCameraRotationActive();
    }

    private boolean isCameraRotationActive() {
        return Math.abs(cameraRotationRadians) > 0.001;
    }

    private RenderCommand createFrontFace(int depth, int side, int worldX, int worldY, Library.TileType tileType) {
        double z = depth - 0.5;
        double leftX = side - 0.5;
        double rightX = side + 0.5;
        if (shouldCullFace(leftX, z, rightX, z, rightX, z, leftX, z)) {
            return null;
        }
        Polygon polygon = polygonFrom(project(leftX, z, 1.0), project(rightX, z, 1.0), project(rightX, z, -1.0), project(leftX, z, -1.0));
        return new RenderCommand(FaceType.FRONT, tileType, depth, side, worldX, worldY, polygon, z);
    }

    private RenderCommand createLeftFace(int depth, int side, int worldX, int worldY, Library.TileType tileType) {
        double x = side - 0.5;
        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;
        if (shouldCullFace(x, nearZ, x, farZ, x, farZ, x, nearZ)) {
            return null;
        }
        Polygon polygon = polygonFrom(project(x, nearZ, 1.0), project(x, farZ, 1.0), project(x, farZ, -1.0), project(x, nearZ, -1.0));
        double sortZ = (nearZ + farZ) / 2.0;
        return new RenderCommand(FaceType.LEFT, tileType, depth, side, worldX, worldY, polygon, sortZ);
    }

    private RenderCommand createRightFace(int depth, int side, int worldX, int worldY, Library.TileType tileType) {
        double x = side + 0.5;
        double nearZ = depth - 0.5;
        double farZ = depth + 0.5;
        if (shouldCullFace(x, nearZ, x, farZ, x, farZ, x, nearZ)) {
            return null;
        }
        Polygon polygon = polygonFrom(project(x, nearZ, 1.0), project(x, farZ, 1.0), project(x, farZ, -1.0), project(x, nearZ, -1.0));
        double sortZ = (nearZ + farZ) / 2.0;
        return new RenderCommand(FaceType.RIGHT, tileType, depth, side, worldX, worldY, polygon, sortZ);
    }

    private List<RenderCommand> buildEntityRenderCommands(List<MapEntity> entities) {
        List<RenderCommand> commands = new ArrayList<>();
        if (entities == null) {
            return commands;
        }
        for (MapEntity entity : entities) {
            /*
             * Animation support is staying here for later.
             *
             * For now, we are using a static overworld image instead.
             */
            BufferedImage entityImage = entity.getStaticImage();

    /*
    if (entity.getIdleAnimation() != null) {
        entityImage = entity.getIdleAnimation().getCurrentFrame();
    }
    */

            if (entityImage == null) {
                continue;
            }

            RelativePosition relative = getRelativePosition(entity.getX(), entity.getY());
            if (relative.forward <= 0 || relative.forward > MAX_DEPTH) {
                continue;
            }
            int sideLimit = relative.forward + SIDE_MARGIN;
            if (Math.abs(relative.side) > sideLimit) {
                continue;
            } /* * Simple visibility check: * Do not draw an entity if a wall exists on the same relative tile. * Later we can improve this into line-of-sight / occlusion. */
            if (isWallAtRelative(relative.forward, relative.side)) {
                continue;
            }
//            BufferedImage frame = entity.getIdleAnimation().getCurrentFrame();
            double z = relative.forward;
            double x = relative.side;
            if (transformDepth(x, z) <= NEAR_CLIP) {
                continue;
            }
            Point bottomCenter = project(x, z, -1.0);
            Point topCenter = project(x, z, 0.55);
            int spriteHeight = Math.abs(bottomCenter.y - topCenter.y);
            if (entity.getType() == Library.EntityType.ITEM || entity.getType() == Library.EntityType.TRAP) {
                spriteHeight = Math.max(16, (int) Math.round(spriteHeight * 0.45));
            }
            int spriteWidth = spriteHeight;
            int screenX = bottomCenter.x - spriteWidth / 2;
            int screenY = bottomCenter.y - spriteHeight;
            Polygon spriteBounds = rectanglePolygon(screenX, screenY, spriteWidth, spriteHeight);
//            commands.add(new RenderCommand(frame, relative.forward, relative.side, entity.getX(), entity.getY(), spriteBounds, z));
            commands.add(new RenderCommand(
                    entityImage,
                    relative.forward,
                    relative.side,
                    entity.getX(),
                    entity.getY(),
                    spriteBounds,
                    z
            ));
        }
        return commands;
    }

    private void drawRenderCommand(Graphics2D g, RenderCommand command) {
        if (command.faceType == FaceType.SPRITE) {
            drawSpriteCommand(g, command);
            return;
        }

        drawBaseTileFace(g, command);
        drawDoorOverlayIfNeeded(g, command);

        g.setColor(Color.BLACK);
        g.drawPolygon(command.polygon);
    }

    private void drawBaseTileFace(Graphics2D g, RenderCommand command) {
        BufferedImage waterTexture = getWaterTexture(command);

        if (waterTexture != null) {
            drawTexturedFace(g, waterTexture, command);
            return;
        }

        Color color = getRenderColor(command);
        TextureManager.SelectedTexture texture = getTextureForCommand(command);

        if (texture != null) {
            drawSelectedTexture(g, texture, command, color);
        } else {
            g.setColor(color);
            g.fillPolygon(command.polygon);
        }
    }

    private BufferedImage getWaterTexture(RenderCommand command) {
        if (command.faceType != FaceType.FLOOR || command.tileType == null) {
            return null;
        }

        if (command.tileType == Library.TileType.WATER) {
            int frame = (int) ((System.currentTimeMillis() / 220L) % decorativeWaterFrames.length);
            return decorativeWaterFrames[frame];
        }

        if (command.tileType == Library.TileType.FISHING_WATER) {
            int frame = (int) ((System.currentTimeMillis() / 260L) % fishingWaterFrames.length);
            return fishingWaterFrames[frame];
        }

        return null;
    }

    private BufferedImage[] loadNumberedFrames(String prefix, int startInclusive, int endInclusive) {
        int frameCount = Math.max(0, endInclusive - startInclusive + 1);
        BufferedImage[] frames = new BufferedImage[frameCount];

        for (int i = 0; i < frameCount; i++) {
            frames[i] = AssetLoader.loadImage(WATER_PATH + prefix + (startInclusive + i) + ".png");
        }

        return frames;
    }

    private void drawSelectedTexture(
            Graphics2D g,
            TextureManager.SelectedTexture texture,
            RenderCommand command,
            Color fallbackColor
    ) {
        if (!shouldDrawSideVariantInset(command, texture)) {
            drawTexturedFace(g, texture.image(), command);
            return;
        }

        BufferedImage baseTexture = getDefaultWallTexture(command);
        if (baseTexture != null) {
            drawTexturedFace(g, baseTexture, command);
        } else {
            g.setColor(fallbackColor);
            g.fillPolygon(command.polygon);
        }

        drawTextureSection(g, texture.image(), createSideVariantOverlayPolygon(command.polygon, texture.image()), 0.0, 1.0);
    }

    private boolean shouldDrawSideVariantInset(RenderCommand command, TextureManager.SelectedTexture texture) {
        if (texture.defaultTexture()) {
            return false;
        }

        return command.faceType == FaceType.LEFT || command.faceType == FaceType.RIGHT;
    }

    private void drawDoorOverlayIfNeeded(Graphics2D g, RenderCommand command) {
        if (command.tileType != Library.TileType.DOOR_CLOSED) {
            return;
        }

        /*
         * Only front-facing closed doors get the visible door overlay.
         * Side faces remain normal wall texture, which looks better in perspective.
         */
        if (command.faceType != FaceType.FRONT) {
            return;
        }

        BufferedImage doorTexture = getDoorOverlayTexture(command);

        if (doorTexture == null) {
            return;
        }

        Polygon doorPolygon = createDoorOverlayPolygon(command.polygon);

        drawTextureSection(g, doorTexture, doorPolygon, 0.0, 1.0);
    }

    private BufferedImage getDoorOverlayTexture(RenderCommand command) {
        if (textureManager == null) {
            return null;
        }

        EnvironmentTheme.TextureTheme doorTheme = getEnvironmentTheme(command).door();

        BufferedImage texture = textureManager.getDefaultTexture(
                doorTheme.location(),
                doorTheme.material1(),
                doorTheme.material2(),
                doorTheme.side()
        );

        if (texture == null && !"center".equals(doorTheme.side())) {
            texture = textureManager.getDefaultTexture(
                    doorTheme.location(),
                    doorTheme.material1(),
                    doorTheme.material2(),
                    "center"
            );
        }

        return texture;
    }

    private Polygon createDoorOverlayPolygon(Polygon wallPolygon) {
        if (wallPolygon == null || wallPolygon.npoints != 4) {
            return wallPolygon;
        }

        /*
         * Expected polygon point order:
         *
         * 0 = top-left
         * 1 = top-right
         * 2 = bottom-right
         * 3 = bottom-left
         *
         * This creates a smaller rectangle inside the wall face.
         * The transparent door PNG is then perspective-mapped onto that rectangle.
         */
        Point topLeft = new Point(wallPolygon.xpoints[0], wallPolygon.ypoints[0]);
        Point topRight = new Point(wallPolygon.xpoints[1], wallPolygon.ypoints[1]);
        Point bottomRight = new Point(wallPolygon.xpoints[2], wallPolygon.ypoints[2]);
        Point bottomLeft = new Point(wallPolygon.xpoints[3], wallPolygon.ypoints[3]);

        Point doorTopLeft = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                DOOR_OVERLAY_LEFT,
                DOOR_OVERLAY_TOP
        );

        Point doorTopRight = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                DOOR_OVERLAY_RIGHT,
                DOOR_OVERLAY_TOP
        );

        Point doorBottomRight = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                DOOR_OVERLAY_RIGHT,
                DOOR_OVERLAY_BOTTOM
        );

        Point doorBottomLeft = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                DOOR_OVERLAY_LEFT,
                DOOR_OVERLAY_BOTTOM
        );

        return polygonFrom(
                doorTopLeft,
                doorTopRight,
                doorBottomRight,
                doorBottomLeft
        );
    }

    private Polygon createSideVariantOverlayPolygon(Polygon wallPolygon, BufferedImage texture) {
        if (wallPolygon == null || wallPolygon.npoints != 4) {
            return wallPolygon;
        }

        Point topLeft = new Point(wallPolygon.xpoints[0], wallPolygon.ypoints[0]);
        Point topRight = new Point(wallPolygon.xpoints[1], wallPolygon.ypoints[1]);
        Point bottomRight = new Point(wallPolygon.xpoints[2], wallPolygon.ypoints[2]);
        Point bottomLeft = new Point(wallPolygon.xpoints[3], wallPolygon.ypoints[3]);

        double wallHeight = (
                distance(topLeft, bottomLeft)
                        + distance(topRight, bottomRight)
        ) / 2.0;
        double wallWidth = (
                distance(topLeft, topRight)
                        + distance(bottomLeft, bottomRight)
        ) / 2.0;
        double textureAspect = texture.getWidth() / (double) texture.getHeight();
        double horizontalSpan = wallWidth <= 0.0
                ? 1.0
                : Math.min(1.0, (wallHeight * textureAspect) / wallWidth);
        double overlayLeft = (1.0 - horizontalSpan) / 2.0;
        double overlayRight = overlayLeft + horizontalSpan;

        Point overlayTopLeft = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                overlayLeft,
                0.0
        );

        Point overlayTopRight = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                overlayRight,
                0.0
        );

        Point overlayBottomRight = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                overlayRight,
                1.0
        );

        Point overlayBottomLeft = pointOnQuad(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                overlayLeft,
                1.0
        );

        return polygonFrom(
                overlayTopLeft,
                overlayTopRight,
                overlayBottomRight,
                overlayBottomLeft
        );
    }

    private double distance(Point first, Point second) {
        return Math.hypot(first.x - second.x, first.y - second.y);
    }

    private Point pointOnQuad(
            Point topLeft,
            Point topRight,
            Point bottomRight,
            Point bottomLeft,
            double horizontalPercent,
            double verticalPercent
    ) {
        Point top = lerpPoint(topLeft, topRight, horizontalPercent);
        Point bottom = lerpPoint(bottomLeft, bottomRight, horizontalPercent);

        return lerpPoint(top, bottom, verticalPercent);
    }

    private void drawSpriteCommand(Graphics2D g, RenderCommand command) {
        if (command.spriteImage == null) {
            return;
        }
        Rectangle bounds = command.polygon.getBounds();
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(command.spriteImage, bounds.x, bounds.y, bounds.width, bounds.height, null);
        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private void drawTexturedFace(Graphics2D g, BufferedImage texture, RenderCommand command) {
        if (command.polygon.npoints != 4) {
            g.setColor(getRenderColor(command));
            g.fillPolygon(command.polygon);
            return;
        } /* * Important: * We deliberately map the texture once. * * Repeating the closest side walls caused the exact double-texture issue * you were seeing. Decorative variants, banners, and unique wall panels * should not be tiled unless we explicitly create a separate tiling mode. */
        drawTextureSection(g, texture, command.polygon, 0.0, 1.0);
    }

    private void drawTextureSection(Graphics2D g, BufferedImage texture, Polygon polygon, double uStart, double uEnd) {
        Point topLeft = new Point(polygon.xpoints[0], polygon.ypoints[0]);
        Point topRight = new Point(polygon.xpoints[1], polygon.ypoints[1]);
        Point bottomRight = new Point(polygon.xpoints[2], polygon.ypoints[2]);
        Point bottomLeft = new Point(polygon.xpoints[3], polygon.ypoints[3]);
        int strips = textureStripCount(polygon);
        Shape oldClip = g.getClip();
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < strips; i++) {
            double localA = i / (double) strips;
            double localB = (i + 1) / (double) strips;
            double uA = lerp(uStart, uEnd, localA);
            double uB = lerp(uStart, uEnd, localB);
            Point stripTopLeft = lerpPoint(topLeft, topRight, uA);
            Point stripTopRight = lerpPoint(topLeft, topRight, uB);
            Point stripBottomRight = lerpPoint(bottomLeft, bottomRight, uB);
            Point stripBottomLeft = lerpPoint(bottomLeft, bottomRight, uA);
            Polygon stripPolygon = polygonFrom(stripTopLeft, stripTopRight, stripBottomRight, stripBottomLeft);
            Rectangle bounds = stripPolygon.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0) {
                continue;
            }
            int sx1 = (int) Math.round(localA * texture.getWidth());
            int sx2 = (int) Math.round(localB * texture.getWidth());
            sx1 = Math.max(0, Math.min(texture.getWidth() - 1, sx1));
            sx2 = Math.max(sx1 + 1, Math.min(texture.getWidth(), sx2));
            g.setClip(stripPolygon);
            g.drawImage(texture, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, sx1, 0, sx2, texture.getHeight(), null);
        }
        g.setClip(oldClip);
        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private int textureStripCount(Polygon polygon) {
        Rectangle bounds = polygon.getBounds();
        int majorAxis = Math.max(bounds.width, bounds.height);
        int widthBasedStrips = Math.max(MIN_TEXTURE_STRIPS, majorAxis / 14);
        return Math.min(MAX_TEXTURE_STRIPS, widthBasedStrips);
    }

    private TextureManager.SelectedTexture getTextureForCommand(RenderCommand command) {
        if (textureManager == null) {
            return null;
        }
        if (command.faceType == FaceType.SPRITE) {
            return null;
        }
        EnvironmentTheme environmentTheme = getEnvironmentTheme(command);
        if (command.faceType == FaceType.FLOOR) {
            EnvironmentTheme.TextureTheme floorTheme = environmentTheme.floor();
            return textureManager.getSelectedTexture(
                    floorTheme.location(),
                    floorTheme.material1(),
                    floorTheme.material2(),
                    floorTheme.side(),
                    command.worldX,
                    command.worldY
            );
        }
        EnvironmentTheme.TextureTheme wallTheme = environmentTheme.wall();
        String sideName = getWallTextureSideName(command);
        TextureManager.SelectedTexture texture = textureManager.getSelectedTexture(
                wallTheme.location(),
                wallTheme.material1(),
                wallTheme.material2(),
                sideName,
                command.worldX,
                command.worldY
        );

        if (texture == null && !sideName.equals("center")) {
            texture = textureManager.getSelectedTexture(
                    wallTheme.location(),
                    wallTheme.material1(),
                    wallTheme.material2(),
                    "center",
                    command.worldX,
                    command.worldY
            );
        }
        return texture;
    }

    private BufferedImage getDefaultWallTexture(RenderCommand command) {
        if (textureManager == null) {
            return null;
        }

        EnvironmentTheme.TextureTheme wallTheme = getEnvironmentTheme(command).wall();
        String sideName = getWallTextureSideName(command);
        BufferedImage texture = textureManager.getDefaultTexture(
                wallTheme.location(),
                wallTheme.material1(),
                wallTheme.material2(),
                sideName
        );

        if (texture == null && !sideName.equals("center")) {
            texture = textureManager.getDefaultTexture(
                    wallTheme.location(),
                    wallTheme.material1(),
                    wallTheme.material2(),
                    "center"
            );
        }

        return texture;
    }

    private EnvironmentTheme getEnvironmentTheme(RenderCommand command) {
        if (map == null || command == null) {
            return getEnvironmentTheme(0);
        }

        return getEnvironmentTheme(map.getEnvironmentThemeIndex(command.worldX, command.worldY));
    }

    private EnvironmentTheme getEnvironmentTheme(int environmentThemeIndex) {
        if (environmentThemes.isEmpty()) {
            return EnvironmentTheme.defaultTheme();
        }

        if (environmentThemeIndex < 0 || environmentThemeIndex >= environmentThemes.size()) {
            return environmentThemes.getFirst();
        }

        return environmentThemes.get(environmentThemeIndex);
    }

    private String getWallTextureSideName(RenderCommand command) {
        /*
         * FaceType.LEFT / FaceType.RIGHT means the physical side face of a wall block.
         * Your asset suffix _left / _right means a visual panel/corner variant.
         * Those are not the same concept.
         */
        if (command.faceType == FaceType.LEFT || command.faceType == FaceType.RIGHT) {
            return "center";
        }
        if (command.side < 0) {
            return "left";
        }
        if (command.side > 0) {
            return "right";
        }
        return "center";
    }

    private Color getRenderColor(RenderCommand command) {
        return switch (command.faceType) {
            case FLOOR -> getFloorColor(command);
            case FRONT, LEFT, RIGHT -> getWallColor(command);
            case SPRITE -> Color.MAGENTA;
        };
    }

    private Color getFloorColor(RenderCommand command) {
        int base = switch (command.depth) {
            case 1 -> 65;
            case 2 -> 55;
            case 3 -> 45;
            case 4 -> 35;
            default -> 25;
        };
        return new Color(base, Math.max(0, base - 10), Math.max(0, base - 20));
    }

    private Color getWallColor(RenderCommand command) {
        if (command.tileType == Library.TileType.DOOR_CLOSED) {
            int base = switch (command.depth) {
                case 1 -> 110;
                case 2 -> 85;
                case 3 -> 65;
                case 4 -> 50;
                default -> 35;
            };
            return switch (command.faceType) {
                case FRONT -> new Color(Math.min(255, base + 25), Math.max(0, base - 15), Math.max(0, base - 35));
                case LEFT -> new Color(base, Math.max(0, base - 25), Math.max(0, base - 40));
                case RIGHT -> new Color(Math.min(255, base + 10), Math.max(0, base - 20), Math.max(0, base - 35));
                case FLOOR, SPRITE -> Color.MAGENTA;
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
            case FRONT -> new Color(base, base, Math.min(255, base + 15));
            case LEFT -> new Color(Math.max(0, base - 20), Math.max(0, base - 20), base);
            case RIGHT -> new Color(Math.max(0, base - 10), Math.max(0, base - 10), Math.min(255, base + 5));
            case FLOOR, SPRITE -> Color.MAGENTA;
        };
    }

    private RelativePosition getRelativePosition(int worldX, int worldY) {
        int dx = worldX - playerX;
        int dy = worldY - playerY;
        int forward = dx * forwardX() + dy * forwardY();
        int side = dx * rightX() + dy * rightY();
        return new RelativePosition(forward, side);
    }

    private Point worldPointAtRelative(int forward, int side) {
        int x = playerX + forwardX() * forward + rightX() * side;
        int y = playerY + forwardY() * forward + rightY() * side;
        return new Point(x, y);
    }

    private Library.TileType getTileAtRelative(int forward, int side) {
        Point point = worldPointAtRelative(forward, side);
        return map.getTile(point.x, point.y);
    }

    private boolean isWallAtRelative(int forward, int side) {
        Point point = worldPointAtRelative(forward, side);
        return isWall(point.x, point.y);
    }

    private boolean isWall(int x, int y) {
        return map.isWallLike(x, y);
    }

    private boolean shouldCullFace(double... coordinates) {
        boolean anyBehind = false;
        boolean anyVisible = false;

        for (int i = 0; i < coordinates.length; i += 2) {
            double depth = transformDepth(coordinates[i], coordinates[i + 1]);

            if (depth <= NEAR_CLIP) {
                anyBehind = true;
            } else {
                anyVisible = true;
            }
        }

        return !anyVisible || (isCameraRotationActive() && anyBehind);
    }

    private double transformDepth(double x, double z) {
        z -= cameraOffsetForward;

        if (isCameraRotationActive()) {
            double sin = Math.sin(cameraRotationRadians);
            double cos = Math.cos(cameraRotationRadians);
            return (x - cameraOffsetSide) * sin + z * cos;
        }

        return z;
    }

    private Point project(double x, double z, double vertical) {
        x -= cameraOffsetSide;
        z -= cameraOffsetForward;

        if (isCameraRotationActive()) {
            double cos = Math.cos(cameraRotationRadians);
            double sin = Math.sin(cameraRotationRadians);
            double rotatedX = x * cos - z * sin;
            double rotatedZ = x * sin + z * cos;
            x = rotatedX;
            z = rotatedZ;
        }

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

    private Polygon rectanglePolygon(int x, int y, int width, int height) {
        Polygon polygon = new Polygon();
        polygon.addPoint(x, y);
        polygon.addPoint(x + width, y);
        polygon.addPoint(x + width, y + height);
        polygon.addPoint(x, y + height);
        return polygon;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Point lerpPoint(Point a, Point b, double t) {
        return new Point((int) Math.round(lerp(a.x, b.x, t)), (int) Math.round(lerp(a.y, b.y, t)));
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

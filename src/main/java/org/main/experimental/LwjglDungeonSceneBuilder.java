package org.main.experimental;

import org.main.core.Library;
import org.main.engine.AssetLoader;
import org.main.engine.DungeonMap;
import org.main.engine.DungeonRenderContext;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.TextureManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class LwjglDungeonSceneBuilder {
    private static final String WATER_PATH = "assets/images/monster/Nov-2015/dngn/water/";
    private static final long DECORATIVE_WATER_FRAME_MS = 220L;
    private static final long FISHING_WATER_FRAME_MS = 260L;
    private static final double DOOR_OVERLAY_LEFT = 0.23;
    private static final double DOOR_OVERLAY_RIGHT = 0.77;
    private static final double DOOR_OVERLAY_TOP = 0.16;
    private static final double DOOR_OVERLAY_BOTTOM = 0.98;
    private static final double DOOR_OVERLAY_OFFSET = 0.004;
    private static final double OPEN_DOOR_EDGE_OFFSET = 0.06;
    private static final double OPEN_DOOR_LEAF_MIN = 0.18;
    private static final double OPEN_DOOR_LEAF_MAX = 0.82;
    private static final double OPEN_DOOR_TOP = 0.86;
    private static final double OPEN_DOOR_BOTTOM = 0.02;

    private final BufferedImage[] decorativeWaterFrames = loadNumberedFrames("shoals_shallow_water", 0, 11);
    private final BufferedImage[] fishingWaterFrames = {
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance1.png"),
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance2.png"),
            AssetLoader.loadImage(WATER_PATH + "shoals_shallow_water_disturbance3.png")
    };
    private final TextureManager textureManager;
    private List<EnvironmentTheme> environmentThemes;

    LwjglDungeonSceneBuilder(TextureManager textureManager, List<EnvironmentTheme> environmentThemes) {
        this.textureManager = textureManager;
        this.environmentThemes = environmentThemes == null || environmentThemes.isEmpty()
                ? List.of(EnvironmentTheme.defaultTheme())
                : new ArrayList<>(environmentThemes);
    }

    void setEnvironmentThemes(List<EnvironmentTheme> environmentThemes) {
        this.environmentThemes = environmentThemes == null || environmentThemes.isEmpty()
                ? List.of(EnvironmentTheme.defaultTheme())
                : new ArrayList<>(environmentThemes);
    }

    Scene build(DungeonRenderContext context, int maxDepth, double wallHeight, double cameraYawDegrees) {
        List<TexturedQuad> quads = new ArrayList<>();
        int floorQuads = 0;
        int wallQuads = 0;
        int spriteQuads = 0;
        int visibleTiles = 0;
        DungeonMap map = context.map();
        int minX = Math.max(0, context.playerX() - maxDepth - 1);
        int maxX = Math.min(map.getWidth() - 1, context.playerX() + maxDepth + 1);
        int minY = Math.max(0, context.playerY() - maxDepth - 1);
        int maxY = Math.min(map.getHeight() - 1, context.playerY() + maxDepth + 1);
        double maxDistanceSquared = (maxDepth + 1.5) * (maxDepth + 1.5);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double centerDx = x + 0.5 - (context.playerX() + 0.5);
                double centerDz = y + 0.5 - (context.playerY() + 0.5);
                if (centerDx * centerDx + centerDz * centerDz > maxDistanceSquared) {
                    continue;
                }

                Library.TileType tileType = map.getTile(x, y);
                visibleTiles++;
                if (!tileType.isWallLike()) {
                    quads.add(floorQuad(context, tileType, x, y));
                    floorQuads++;
                    TexturedQuad openDoor = openDoorQuad(context, tileType, x, y, wallHeight);
                    if (openDoor != null) {
                        quads.add(openDoor);
                        wallQuads++;
                    }
                    continue;
                }

                wallQuads += addWallFace(quads, context, tileType, x, y, Face.NORTH, wallHeight, !map.isWallLike(x, y - 1));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.EAST, wallHeight, !map.isWallLike(x + 1, y));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.SOUTH, wallHeight, !map.isWallLike(x, y + 1));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.WEST, wallHeight, !map.isWallLike(x - 1, y));
            }
        }

        for (MapEntity entity : context.entities()) {
            TexturedQuad sprite = spriteQuad(context, entity, maxDepth, cameraYawDegrees);
            if (sprite != null) {
                quads.add(sprite);
                spriteQuads++;
            }
        }

        return new Scene(quads, visibleTiles, floorQuads, wallQuads, spriteQuads);
    }

    private int addWallFace(
            List<TexturedQuad> quads,
            DungeonRenderContext context,
            Library.TileType tileType,
            int x,
            int y,
            Face face,
            double wallHeight,
            boolean exposed
    ) {
        if (!exposed) {
            return 0;
        }

        quads.add(wallQuad(context, tileType, x, y, face, wallHeight));
        int added = 1;
        TexturedQuad doorOverlay = doorOverlayQuad(context, tileType, x, y, face, wallHeight);
        if (doorOverlay != null) {
            quads.add(doorOverlay);
            added++;
        }
        return added;
    }

    private TexturedQuad floorQuad(DungeonRenderContext context, Library.TileType tileType, int x, int y) {
        return new TexturedQuad(
                floorTextureFor(context, tileType, x, y),
                QuadKind.FLOOR,
                new Vertex(x, 0.0, y, 0.0, 1.0),
                new Vertex(x + 1.0, 0.0, y, 1.0, 1.0),
                new Vertex(x + 1.0, 0.0, y + 1.0, 1.0, 0.0),
                new Vertex(x, 0.0, y + 1.0, 0.0, 0.0)
        );
    }

    private BufferedImage floorTextureFor(DungeonRenderContext context, Library.TileType tileType, int x, int y) {
        BufferedImage waterTexture = waterTextureFor(tileType);
        return waterTexture == null ? textureFor(context, tileType, Face.FLOOR, x, y) : waterTexture;
    }

    private BufferedImage waterTextureFor(Library.TileType tileType) {
        if (tileType == Library.TileType.WATER && decorativeWaterFrames.length > 0) {
            int frame = (int) ((System.currentTimeMillis() / DECORATIVE_WATER_FRAME_MS) % decorativeWaterFrames.length);
            return decorativeWaterFrames[frame];
        }

        if (tileType == Library.TileType.FISHING_WATER && fishingWaterFrames.length > 0) {
            int frame = (int) ((System.currentTimeMillis() / FISHING_WATER_FRAME_MS) % fishingWaterFrames.length);
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

    private TexturedQuad wallQuad(
            DungeonRenderContext context,
            Library.TileType tileType,
            int x,
            int y,
            Face face,
            double wallHeight
    ) {
        BufferedImage texture = wallTextureFor(context, tileType, face, x, y);
        return switch (face) {
            case NORTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x, wallHeight, y, 0.0, 1.0),
                    new Vertex(x + 1.0, wallHeight, y, 1.0, 1.0),
                    new Vertex(x + 1.0, 0.0, y, 1.0, 0.0),
                    new Vertex(x, 0.0, y, 0.0, 0.0)
            );
            case EAST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + 1.0, wallHeight, y, 0.0, 1.0),
                    new Vertex(x + 1.0, wallHeight, y + 1.0, 1.0, 1.0),
                    new Vertex(x + 1.0, 0.0, y + 1.0, 1.0, 0.0),
                    new Vertex(x + 1.0, 0.0, y, 0.0, 0.0)
            );
            case SOUTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + 1.0, wallHeight, y + 1.0, 0.0, 1.0),
                    new Vertex(x, wallHeight, y + 1.0, 1.0, 1.0),
                    new Vertex(x, 0.0, y + 1.0, 1.0, 0.0),
                    new Vertex(x + 1.0, 0.0, y + 1.0, 0.0, 0.0)
            );
            case WEST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x, wallHeight, y + 1.0, 0.0, 1.0),
                    new Vertex(x, wallHeight, y, 1.0, 1.0),
                    new Vertex(x, 0.0, y, 1.0, 0.0),
                    new Vertex(x, 0.0, y + 1.0, 0.0, 0.0)
            );
            default -> throw new IllegalArgumentException("Unsupported wall face: " + face);
        };
    }

    private TexturedQuad doorOverlayQuad(
            DungeonRenderContext context,
            Library.TileType tileType,
            int x,
            int y,
            Face face,
            double wallHeight
    ) {
        if (!isClosedDoor(tileType)) {
            return null;
        }

        BufferedImage texture = doorTextureFor(context, x, y);
        if (texture == null) {
            return null;
        }

        double left = DOOR_OVERLAY_LEFT;
        double right = DOOR_OVERLAY_RIGHT;
        double topY = wallHeight * (1.0 - DOOR_OVERLAY_TOP);
        double bottomY = wallHeight * (1.0 - DOOR_OVERLAY_BOTTOM);

        return switch (face) {
            case NORTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + left, topY, y - DOOR_OVERLAY_OFFSET, 0.0, 1.0),
                    new Vertex(x + right, topY, y - DOOR_OVERLAY_OFFSET, 1.0, 1.0),
                    new Vertex(x + right, bottomY, y - DOOR_OVERLAY_OFFSET, 1.0, 0.0),
                    new Vertex(x + left, bottomY, y - DOOR_OVERLAY_OFFSET, 0.0, 0.0)
            );
            case EAST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + 1.0 + DOOR_OVERLAY_OFFSET, topY, y + left, 0.0, 1.0),
                    new Vertex(x + 1.0 + DOOR_OVERLAY_OFFSET, topY, y + right, 1.0, 1.0),
                    new Vertex(x + 1.0 + DOOR_OVERLAY_OFFSET, bottomY, y + right, 1.0, 0.0),
                    new Vertex(x + 1.0 + DOOR_OVERLAY_OFFSET, bottomY, y + left, 0.0, 0.0)
            );
            case SOUTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + right, topY, y + 1.0 + DOOR_OVERLAY_OFFSET, 0.0, 1.0),
                    new Vertex(x + left, topY, y + 1.0 + DOOR_OVERLAY_OFFSET, 1.0, 1.0),
                    new Vertex(x + left, bottomY, y + 1.0 + DOOR_OVERLAY_OFFSET, 1.0, 0.0),
                    new Vertex(x + right, bottomY, y + 1.0 + DOOR_OVERLAY_OFFSET, 0.0, 0.0)
            );
            case WEST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x - DOOR_OVERLAY_OFFSET, topY, y + right, 0.0, 1.0),
                    new Vertex(x - DOOR_OVERLAY_OFFSET, topY, y + left, 1.0, 1.0),
                    new Vertex(x - DOOR_OVERLAY_OFFSET, bottomY, y + left, 1.0, 0.0),
                    new Vertex(x - DOOR_OVERLAY_OFFSET, bottomY, y + right, 0.0, 0.0)
            );
            default -> null;
        };
    }

    private TexturedQuad openDoorQuad(
            DungeonRenderContext context,
            Library.TileType tileType,
            int x,
            int y,
            double wallHeight
    ) {
        if (!isOpenDoor(tileType)) {
            return null;
        }

        BufferedImage texture = doorTextureFor(context, x, y);
        if (texture == null) {
            return null;
        }

        double bottomY = wallHeight * OPEN_DOOR_BOTTOM;
        double topY = wallHeight * OPEN_DOOR_TOP;
        boolean eastWestDoorway = context.map().isWallLike(x, y - 1) || context.map().isWallLike(x, y + 1);
        if (eastWestDoorway) {
            double z = y + OPEN_DOOR_EDGE_OFFSET;
            return new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + OPEN_DOOR_LEAF_MIN, topY, z, 0.0, 1.0),
                    new Vertex(x + OPEN_DOOR_LEAF_MAX, topY, z, 1.0, 1.0),
                    new Vertex(x + OPEN_DOOR_LEAF_MAX, bottomY, z, 1.0, 0.0),
                    new Vertex(x + OPEN_DOOR_LEAF_MIN, bottomY, z, 0.0, 0.0)
            );
        }

        double doorX = x + OPEN_DOOR_EDGE_OFFSET;
        return new TexturedQuad(
                texture,
                QuadKind.WALL,
                new Vertex(doorX, topY, y + OPEN_DOOR_LEAF_MAX, 0.0, 1.0),
                new Vertex(doorX, topY, y + OPEN_DOOR_LEAF_MIN, 1.0, 1.0),
                new Vertex(doorX, bottomY, y + OPEN_DOOR_LEAF_MIN, 1.0, 0.0),
                new Vertex(doorX, bottomY, y + OPEN_DOOR_LEAF_MAX, 0.0, 0.0)
        );
    }

    private TexturedQuad spriteQuad(
            DungeonRenderContext context,
            MapEntity entity,
            int maxDepth,
            double cameraYawDegrees
    ) {
        BufferedImage image = entity.getIdleAnimation() == null
                ? entity.getStaticImage()
                : entity.getIdleAnimation().getCurrentFrame();
        if (image == null) {
            return null;
        }

        double dx = entity.getX() + 0.5 - (context.playerX() + 0.5);
        double dz = entity.getY() + 0.5 - (context.playerY() + 0.5);
        if (dx * dx + dz * dz > maxDepth * maxDepth) {
            return null;
        }

        if (context.map().getTile(entity.getX(), entity.getY()).isWallLike() && !entity.shouldRenderOnWall()) {
            return null;
        }

        double height = spriteHeightFor(entity);
        double width = height;
        if (image.getHeight() > 0) {
            width = height * (image.getWidth() / (double) image.getHeight());
        }

        RightVector right = rightVector(cameraYawDegrees);
        double centerX = entity.getX() + 0.5;
        double centerZ = entity.getY() + 0.5;
        double halfWidth = width / 2.0;

        return new TexturedQuad(
                image,
                QuadKind.SPRITE,
                new Vertex(centerX - right.x() * halfWidth, height, centerZ - right.z() * halfWidth, 0.0, 1.0),
                new Vertex(centerX + right.x() * halfWidth, height, centerZ + right.z() * halfWidth, 1.0, 1.0),
                new Vertex(centerX + right.x() * halfWidth, 0.0, centerZ + right.z() * halfWidth, 1.0, 0.0),
                new Vertex(centerX - right.x() * halfWidth, 0.0, centerZ - right.z() * halfWidth, 0.0, 0.0)
        );
    }

    private double spriteHeightFor(MapEntity entity) {
        double scale = entity == null ? 1.0 : entity.getVisualScale();
        Library.EntityType type = entity == null ? Library.EntityType.ITEM : entity.getType();

        return switch (type) {
            case ITEM -> Math.max(0.16, 0.36 * scale);
            case TRAP -> Math.max(0.24, 0.55 * scale);
            default -> Math.max(0.18, 0.82 * scale);
        };
    }

    private BufferedImage wallTextureFor(
            DungeonRenderContext context,
            Library.TileType tileType,
            Face face,
            int worldX,
            int worldY
    ) {
        if (!isClosedDoor(tileType)) {
            return textureFor(context, tileType, face, worldX, worldY);
        }

        EnvironmentTheme theme = environmentThemeFor(context.map(), worldX, worldY);
        EnvironmentTheme.TextureTheme wallTheme = theme.wall();
        String sideName = switch (face) {
            case EAST -> "right";
            case WEST -> "left";
            default -> wallTheme.side();
        };
        BufferedImage selected = selectedOrDefaultTexture(wallTheme, sideName, worldX, worldY);
        if (selected != null) {
            return selected;
        }
        return textureFor(context, tileType, face, worldX, worldY);
    }

    private BufferedImage doorTextureFor(DungeonRenderContext context, int worldX, int worldY) {
        EnvironmentTheme.TextureTheme doorTheme = environmentThemeFor(context.map(), worldX, worldY).door();
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

    private boolean isClosedDoor(Library.TileType tileType) {
        return tileType == Library.TileType.DOOR_CLOSED || tileType == Library.TileType.QUEST_DOOR_CLOSED;
    }

    private boolean isOpenDoor(Library.TileType tileType) {
        return tileType == Library.TileType.DOOR_OPEN || tileType == Library.TileType.QUEST_DOOR_OPEN;
    }

    private BufferedImage textureFor(
            DungeonRenderContext context,
            Library.TileType tileType,
            Face face,
            int worldX,
            int worldY
    ) {
        EnvironmentTheme theme = environmentThemeFor(context.map(), worldX, worldY);
        EnvironmentTheme.TextureTheme textureTheme;
        String sideName;

        if (face == Face.FLOOR) {
            textureTheme = theme.floor();
            sideName = textureTheme.side();
        } else if (tileType.isDoor()) {
            textureTheme = theme.door();
            sideName = textureTheme.side();
        } else {
            textureTheme = theme.wall();
            sideName = switch (face) {
                case EAST -> "right";
                case WEST -> "left";
                default -> textureTheme.side();
            };
        }

        return selectedOrDefaultTexture(textureTheme, sideName, worldX, worldY);
    }

    private BufferedImage selectedOrDefaultTexture(
            EnvironmentTheme.TextureTheme textureTheme,
            String sideName,
            int worldX,
            int worldY
    ) {
        TextureManager.SelectedTexture selectedTexture = textureManager.getSelectedTexture(
                textureTheme.location(),
                textureTheme.material1(),
                textureTheme.material2(),
                sideName,
                worldX,
                worldY
        );
        if (selectedTexture != null) {
            return selectedTexture.image();
        }

        return textureManager.getDefaultTexture(
                textureTheme.location(),
                textureTheme.material1(),
                textureTheme.material2(),
                textureTheme.side()
        );
    }

    private EnvironmentTheme environmentThemeFor(DungeonMap map, int worldX, int worldY) {
        int index = map.getEnvironmentThemeIndex(worldX, worldY);
        if (index < 0 || index >= environmentThemes.size()) {
            return environmentThemes.getFirst();
        }
        return environmentThemes.get(index);
    }

    private RightVector rightVector(double cameraYawDegrees) {
        double radians = Math.toRadians(cameraYawDegrees + 90.0);
        return new RightVector(Math.sin(radians), -Math.cos(radians));
    }

    enum QuadKind {
        FLOOR,
        WALL,
        SPRITE
    }

    enum Face {
        NORTH,
        EAST,
        SOUTH,
        WEST,
        FLOOR
    }

    record Scene(
            List<TexturedQuad> quads,
            int visibleTiles,
            int floorQuads,
            int wallQuads,
            int spriteQuads
    ) {
    }

    record TexturedQuad(
            BufferedImage texture,
            QuadKind kind,
            Vertex topLeft,
            Vertex topRight,
            Vertex bottomRight,
            Vertex bottomLeft
    ) {
    }

    record Vertex(double x, double y, double z, double u, double v) {
    }

    private record RightVector(double x, double z) {
    }
}

package org.main.experimental;

import org.main.core.Library;
import org.main.content.PaintBrushLibrary;
import org.main.engine.AssetLoader;
import org.main.engine.DungeonMap;
import org.main.engine.DungeonRenderContext;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MapPaintData;
import org.main.engine.TextureManager;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    Scene build(
            DungeonRenderContext context,
            int maxDepth,
            double wallHeight,
            double roofPitchHeight,
            double cameraYawDegrees
    ) {
        List<TexturedQuad> quads = new ArrayList<>();
        List<ModelInstance> models = new ArrayList<>();
        List<RoofTile> roofTiles = new ArrayList<>();
        int floorQuads = 0;
        int wallQuads = 0;
        int roofQuads = 0;
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
                double tileHeight = tileHeightFor(map, x, y, wallHeight);
                visibleTiles++;
                if (!tileType.isWallLike()) {
                    quads.add(floorQuad(context, tileType, x, y));
                    floorQuads++;
                    TexturedQuad roof = roofQuad(context, x, y, tileHeight);
                    if (roof != null) {
                        quads.add(roof);
                        roofTiles.add(roofTile(context, x, y, tileHeight, roof.texture()));
                        roofQuads++;
                    }
                    TexturedQuad openDoor = openDoorQuad(context, tileType, x, y, tileHeight);
                    if (openDoor != null) {
                        quads.add(openDoor);
                        wallQuads++;
                    }
                    continue;
                }

                wallQuads += addWallFace(quads, context, tileType, x, y, Face.NORTH, tileHeight, neighborWallHeight(map, x, y - 1, wallHeight));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.EAST, tileHeight, neighborWallHeight(map, x + 1, y, wallHeight));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.SOUTH, tileHeight, neighborWallHeight(map, x, y + 1, wallHeight));
                wallQuads += addWallFace(quads, context, tileType, x, y, Face.WEST, tileHeight, neighborWallHeight(map, x - 1, y, wallHeight));
                TexturedQuad roof = roofQuad(context, x, y, tileHeight);
                if (roof != null) {
                    quads.add(roof);
                    roofTiles.add(roofTile(context, x, y, tileHeight, roof.texture()));
                    roofQuads++;
                }
            }
        }

        List<TexturedQuad> roofCaps = roofCapQuads(roofTiles, roofPitchHeight);
        quads.addAll(roofCaps);
        roofQuads += roofCaps.size();

        for (MapEntity entity : context.entities()) {
            TexturedQuad sprite = spriteQuad(context, entity, maxDepth, cameraYawDegrees);
            if (entity.hasVisibleStaticModel()) {
                ModelInstance model = modelInstance(context, entity, maxDepth, sprite);
                if (model != null) {
                    models.add(model);
                    continue;
                }
            }
            if (sprite != null) {
                quads.add(sprite);
                spriteQuads++;
            }
        }

        return new Scene(quads, models, visibleTiles, floorQuads, wallQuads, roofQuads, spriteQuads);
    }

    private ModelInstance modelInstance(
            DungeonRenderContext context,
            MapEntity entity,
            int maxDepth,
            TexturedQuad fallbackSprite
    ) {
        double dx = entity.getX() + 0.5 - (context.playerX() + 0.5);
        double dz = entity.getY() + 0.5 - (context.playerY() + 0.5);
        if (dx * dx + dz * dz > maxDepth * maxDepth) {
            return null;
        }
        if (context.map().getTile(entity.getX(), entity.getY()).isWallLike() && !entity.shouldRenderOnWall()) {
            return null;
        }
        return new ModelInstance(
                entity.getStaticModelPath(),
                entity.getRenderX() + 0.5,
                0.0,
                entity.getRenderY() + 0.5,
                spriteHeightFor(entity),
                fallbackSprite,
                entity.getCharacterModel(),
                entity.isVisuallyMoving()
                        ? org.main.content.CharacterModelDefinition.AnimationSlot.WALK
                        : org.main.content.CharacterModelDefinition.AnimationSlot.IDLE
        );
    }

    private RoofTile roofTile(DungeonRenderContext context, int x, int y, double topY, BufferedImage texture) {
        DungeonMap map = context.map();
        String brushId = map.getPaintBrushId(MapPaintData.Layer.ROOF, x, y);
        return new RoofTile(x, y, map.getHeightLevel(x, y), topY, brushId, texture);
    }

    private List<TexturedQuad> roofCapQuads(List<RoofTile> roofTiles, double roofPitchHeight) {
        if (roofTiles.isEmpty() || roofPitchHeight <= 0.001) {
            return List.of();
        }

        Map<String, Map<Long, RoofTile>> tilesByStyle = new HashMap<>();
        for (RoofTile tile : roofTiles) {
            tilesByStyle.computeIfAbsent(tile.styleKey(), ignored -> new HashMap<>())
                    .put(tile.coordinateKey(), tile);
        }

        List<TexturedQuad> caps = new ArrayList<>();
        for (Map<Long, RoofTile> styleTiles : tilesByStyle.values()) {
            Set<Long> remaining = new HashSet<>(styleTiles.keySet());
            while (!remaining.isEmpty()) {
                long start = remaining.iterator().next();
                RoofBounds bounds = collectRoofBounds(styleTiles, remaining, start);
                caps.addAll(gabledRoofCap(bounds, roofPitchHeight));
            }
        }
        return caps;
    }

    private RoofBounds collectRoofBounds(Map<Long, RoofTile> styleTiles, Set<Long> remaining, long start) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        remaining.remove(start);

        RoofTile first = styleTiles.get(start);
        int minX = first.x();
        int maxX = first.x();
        int minY = first.y();
        int maxY = first.y();

        while (!queue.isEmpty()) {
            RoofTile tile = styleTiles.get(queue.removeFirst());
            minX = Math.min(minX, tile.x());
            maxX = Math.max(maxX, tile.x());
            minY = Math.min(minY, tile.y());
            maxY = Math.max(maxY, tile.y());

            addRoofNeighbor(tile.x() + 1, tile.y(), styleTiles, remaining, queue);
            addRoofNeighbor(tile.x() - 1, tile.y(), styleTiles, remaining, queue);
            addRoofNeighbor(tile.x(), tile.y() + 1, styleTiles, remaining, queue);
            addRoofNeighbor(tile.x(), tile.y() - 1, styleTiles, remaining, queue);
        }

        return new RoofBounds(minX, maxX, minY, maxY, first.topY(), first.texture());
    }

    private void addRoofNeighbor(
            int x,
            int y,
            Map<Long, RoofTile> styleTiles,
            Set<Long> remaining,
            ArrayDeque<Long> queue
    ) {
        long key = RoofTile.coordinateKey(x, y);
        if (remaining.remove(key) && styleTiles.containsKey(key)) {
            queue.add(key);
        }
    }

    private List<TexturedQuad> gabledRoofCap(RoofBounds bounds, double roofPitchHeight) {
        double x0 = bounds.minX();
        double x1 = bounds.maxX() + 1.0;
        double z0 = bounds.minY();
        double z1 = bounds.maxY() + 1.0;
        double baseY = bounds.topY();
        double ridgeY = baseY + roofPitchHeight;
        BufferedImage texture = bounds.texture();
        boolean ridgeRunsEastWest = (x1 - x0) >= (z1 - z0);

        if (ridgeRunsEastWest) {
            double ridgeZ = (z0 + z1) * 0.5;
            return List.of(
                    new TexturedQuad(
                            texture,
                            QuadKind.ROOF,
                            new Vertex(x0, ridgeY, ridgeZ, 0.0, 1.0),
                            new Vertex(x1, ridgeY, ridgeZ, 1.0, 1.0),
                            new Vertex(x1, baseY, z0, 1.0, 0.0),
                            new Vertex(x0, baseY, z0, 0.0, 0.0)
                    ),
                    new TexturedQuad(
                            texture,
                            QuadKind.ROOF,
                            new Vertex(x1, ridgeY, ridgeZ, 0.0, 1.0),
                            new Vertex(x0, ridgeY, ridgeZ, 1.0, 1.0),
                            new Vertex(x0, baseY, z1, 1.0, 0.0),
                            new Vertex(x1, baseY, z1, 0.0, 0.0)
                    ),
                    triangleQuad(
                            texture,
                            new Vertex(x0, ridgeY, ridgeZ, 0.5, 1.0),
                            new Vertex(x0, baseY, z0, 0.0, 0.0),
                            new Vertex(x0, baseY, z1, 1.0, 0.0)
                    ),
                    triangleQuad(
                            texture,
                            new Vertex(x1, ridgeY, ridgeZ, 0.5, 1.0),
                            new Vertex(x1, baseY, z1, 0.0, 0.0),
                            new Vertex(x1, baseY, z0, 1.0, 0.0)
                    )
            );
        }

        double ridgeX = (x0 + x1) * 0.5;
        return List.of(
                new TexturedQuad(
                        texture,
                        QuadKind.ROOF,
                        new Vertex(ridgeX, ridgeY, z1, 0.0, 1.0),
                        new Vertex(ridgeX, ridgeY, z0, 1.0, 1.0),
                        new Vertex(x0, baseY, z0, 1.0, 0.0),
                        new Vertex(x0, baseY, z1, 0.0, 0.0)
                ),
                new TexturedQuad(
                        texture,
                        QuadKind.ROOF,
                        new Vertex(ridgeX, ridgeY, z0, 0.0, 1.0),
                        new Vertex(ridgeX, ridgeY, z1, 1.0, 1.0),
                        new Vertex(x1, baseY, z1, 1.0, 0.0),
                        new Vertex(x1, baseY, z0, 0.0, 0.0)
                ),
                triangleQuad(
                        texture,
                        new Vertex(ridgeX, ridgeY, z0, 0.5, 1.0),
                        new Vertex(x1, baseY, z0, 0.0, 0.0),
                        new Vertex(x0, baseY, z0, 1.0, 0.0)
                ),
                triangleQuad(
                        texture,
                        new Vertex(ridgeX, ridgeY, z1, 0.5, 1.0),
                        new Vertex(x0, baseY, z1, 0.0, 0.0),
                        new Vertex(x1, baseY, z1, 1.0, 0.0)
                )
        );
    }

    private TexturedQuad triangleQuad(BufferedImage texture, Vertex apex, Vertex baseA, Vertex baseB) {
        return new TexturedQuad(texture, QuadKind.ROOF, apex, baseA, baseB, baseB);
    }

    private double tileHeightFor(DungeonMap map, int x, int y, double wallHeight) {
        return Math.max(0.1, wallHeight * map.getHeightMultiplier(x, y));
    }

    private double neighborWallHeight(DungeonMap map, int x, int y, double wallHeight) {
        return map.isWallLike(x, y) ? tileHeightFor(map, x, y, wallHeight) : 0.0;
    }

    private int addWallFace(
            List<TexturedQuad> quads,
            DungeonRenderContext context,
            Library.TileType tileType,
            int x,
            int y,
            Face face,
            double topY,
            double neighborTopY
    ) {
        double bottomY = Math.max(0.0, neighborTopY);
        if (topY <= bottomY + 0.001) {
            return 0;
        }

        quads.add(wallQuad(context, tileType, x, y, face, bottomY, topY));
        int added = 1;
        TexturedQuad doorOverlay = bottomY <= 0.001
                ? doorOverlayQuad(context, tileType, x, y, face, topY)
                : null;
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
            double bottomY,
            double topY
    ) {
        BufferedImage texture = wallTextureFor(context, tileType, face, x, y);
        return switch (face) {
            case NORTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x, topY, y, 0.0, 1.0),
                    new Vertex(x + 1.0, topY, y, 1.0, 1.0),
                    new Vertex(x + 1.0, bottomY, y, 1.0, 0.0),
                    new Vertex(x, bottomY, y, 0.0, 0.0)
            );
            case EAST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + 1.0, topY, y, 0.0, 1.0),
                    new Vertex(x + 1.0, topY, y + 1.0, 1.0, 1.0),
                    new Vertex(x + 1.0, bottomY, y + 1.0, 1.0, 0.0),
                    new Vertex(x + 1.0, bottomY, y, 0.0, 0.0)
            );
            case SOUTH -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x + 1.0, topY, y + 1.0, 0.0, 1.0),
                    new Vertex(x, topY, y + 1.0, 1.0, 1.0),
                    new Vertex(x, bottomY, y + 1.0, 1.0, 0.0),
                    new Vertex(x + 1.0, bottomY, y + 1.0, 0.0, 0.0)
            );
            case WEST -> new TexturedQuad(
                    texture,
                    QuadKind.WALL,
                    new Vertex(x, topY, y + 1.0, 0.0, 1.0),
                    new Vertex(x, topY, y, 1.0, 1.0),
                    new Vertex(x, bottomY, y, 1.0, 0.0),
                    new Vertex(x, bottomY, y + 1.0, 0.0, 0.0)
            );
            default -> throw new IllegalArgumentException("Unsupported wall face: " + face);
        };
    }

    private TexturedQuad roofQuad(DungeonRenderContext context, int x, int y, double topY) {
        BufferedImage texture = roofTextureFor(context, x, y);
        if (texture == null) {
            return null;
        }

        return new TexturedQuad(
                texture,
                QuadKind.ROOF,
                new Vertex(x, topY, y + 1.0, 0.0, 1.0),
                new Vertex(x + 1.0, topY, y + 1.0, 1.0, 1.0),
                new Vertex(x + 1.0, topY, y, 1.0, 0.0),
                new Vertex(x, topY, y, 0.0, 0.0)
        );
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
        BufferedImage brushTexture = brushTextureFor(context.map(), MapPaintData.Layer.WALL, face, worldX, worldY);
        if (brushTexture != null) {
            return brushTexture;
        }

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
        BufferedImage brushTexture = brushTextureFor(context.map(), MapPaintData.Layer.DOOR, Face.FLOOR, worldX, worldY);
        if (brushTexture != null) {
            return brushTexture;
        }

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

    private BufferedImage roofTextureFor(DungeonRenderContext context, int worldX, int worldY) {
        return brushTextureFor(context.map(), MapPaintData.Layer.ROOF, Face.FLOOR, worldX, worldY);
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
        MapPaintData.Layer paintLayer = face == Face.FLOOR ? MapPaintData.Layer.FLOOR : MapPaintData.Layer.WALL;
        BufferedImage brushTexture = brushTextureFor(context.map(), paintLayer, face, worldX, worldY);
        if (brushTexture != null) {
            return brushTexture;
        }

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

    private BufferedImage brushTextureFor(
            DungeonMap map,
            MapPaintData.Layer layer,
            Face face,
            int worldX,
            int worldY
    ) {
        String brushId = map.getPaintBrushId(layer, worldX, worldY);
        if (brushId.isBlank()) {
            return null;
        }

        EnvironmentTheme.TextureTheme textureTheme = PaintBrushLibrary.textureForBrush(brushId);
        if (textureTheme == null) {
            return null;
        }

        String sideName = textureTheme.side();
        if (layer == MapPaintData.Layer.WALL) {
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
        ROOF,
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
            List<ModelInstance> models,
            int visibleTiles,
            int floorQuads,
            int wallQuads,
            int roofQuads,
            int spriteQuads
    ) {
    }

    record ModelInstance(
            String assetPath,
            double centerX,
            double baseY,
            double centerZ,
            double height,
            TexturedQuad fallbackSprite,
            org.main.content.CharacterModelDefinition characterModel,
            org.main.content.CharacterModelDefinition.AnimationSlot animationSlot
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

    private record RoofTile(
            int x,
            int y,
            int heightLevel,
            double topY,
            String brushId,
            BufferedImage texture
    ) {
        private String styleKey() {
            return heightLevel + "|" + (brushId == null ? "" : brushId);
        }

        private long coordinateKey() {
            return coordinateKey(x, y);
        }

        private static long coordinateKey(int x, int y) {
            return (((long) x) << 32) ^ (y & 0xffffffffL);
        }
    }

    private record RoofBounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            double topY,
            BufferedImage texture
    ) {
    }

    private record RightVector(double x, double z) {
    }
}

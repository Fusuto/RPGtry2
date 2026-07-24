package org.main.experimental;

import org.main.core.GameConfiguration;
import org.main.engine.DungeonMap;
import org.main.engine.MapLight;
import org.main.engine.MapLightingSettings;
import org.main.engine.TerrainEdgeKind;
import org.main.engine.TerrainGeometry;

import java.awt.image.BufferedImage;
import java.util.List;

final class LightmapBaker {
    LightmapBakeResult bake(DungeonMap map) {
        long startNanos = System.nanoTime();
        MapLightingSettings settings = map == null ? MapLightingSettings.defaultSettings() : map.getLightingSettings();
        int pixelsPerTile = Math.max(1, GameConfiguration.intValue("lighting.lightmap.pixelsPerTile", 4));
        int width = Math.max(1, map.getWidth() * pixelsPerTile);
        int height = Math.max(1, map.getHeight() * pixelsPerTile);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<MapLight> lights = map.getLightsView();
        int maxLights = Math.max(0, GameConfiguration.intValue("lighting.maxLights", 64));
        boolean lightingEnabled = GameConfiguration.booleanValue("lighting.enabled", true)
                && settings.lightingEnabled();
        boolean occlusionEnabled = GameConfiguration.booleanValue("lighting.occlusion.enabled", true);
        int appliedSamples = 0;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double worldX = (px + 0.5) / pixelsPerTile;
                double worldZ = (py + 0.5) / pixelsPerTile;
                double worldY = TerrainGeometry.groundYAtWorld(map, worldX, worldZ) + 0.15;
                double red = lightingEnabled ? settings.red() * settings.ambientIntensity() : 1.0;
                double green = lightingEnabled ? settings.green() * settings.ambientIntensity() : 1.0;
                double blue = lightingEnabled ? settings.blue() * settings.ambientIntensity() : 1.0;

                if (lightingEnabled) {
                    int visited = 0;
                    int sampleTileX = clamp((int) Math.floor(worldX), 0, map.getWidth() - 1);
                    int sampleTileY = clamp((int) Math.floor(worldZ), 0, map.getHeight() - 1);
                    for (MapLight light : lights) {
                        if (light == null || !light.enabled()) {
                            continue;
                        }
                        if (visited++ >= maxLights) {
                            break;
                        }
                        double lightX = light.x() + 0.5;
                        double lightZ = light.y() + 0.5;
                        double lightY = TerrainGeometry.groundYAtWorld(map, lightX, lightZ) + light.heightOffset();
                        double dx = lightX - worldX;
                        double dy = lightY - worldY;
                        double dz = lightZ - worldZ;
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance > light.radius()) {
                            continue;
                        }
                        if (occlusionEnabled && !hasLineOfSight(map, sampleTileX, sampleTileY, light.x(), light.y())) {
                            continue;
                        }
                        double falloff = 1.0 - distance / light.radius();
                        double contribution = falloff * falloff * light.intensity();
                        red += colorRed(light.colorRgb()) * contribution;
                        green += colorGreen(light.colorRgb()) * contribution;
                        blue += colorBlue(light.colorRgb()) * contribution;
                        appliedSamples++;
                    }
                }

                int argb = 0xFF000000
                        | (((int) Math.round(clamp(red) * 255.0)) << 16)
                        | (((int) Math.round(clamp(green) * 255.0)) << 8)
                        | ((int) Math.round(clamp(blue) * 255.0));
                image.setRGB(px, height - 1 - py, argb);
            }
        }

        double bakeMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        return new LightmapBakeResult(image, pixelsPerTile, bakeMs, appliedSamples);
    }

    private static boolean hasLineOfSight(DungeonMap map, int fromX, int fromY, int targetX, int targetY) {
        if (map == null || map.isOutOfBounds(targetX, targetY)) {
            return false;
        }
        int dx = Math.abs(targetX - fromX);
        int dy = Math.abs(targetY - fromY);
        int sx = fromX < targetX ? 1 : -1;
        int sy = fromY < targetY ? 1 : -1;
        int error = dx - dy;
        int x = fromX;
        int y = fromY;

        while (x != targetX || y != targetY) {
            int previousX = x;
            int previousY = y;
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += sx;
            }
            if (doubledError < dx) {
                error += dx;
                y += sy;
            }
            if (map.isOutOfBounds(x, y)) {
                return false;
            }
            if ((x != targetX || y != targetY) && map.isWallLike(x, y)) {
                return false;
            }
            if (TerrainGeometry.edgeKind(map, previousX, previousY, x, y) == TerrainEdgeKind.CLIFF) {
                return false;
            }
        }
        return true;
    }

    private static double colorRed(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0;
    }

    private static double colorGreen(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0;
    }

    private static double colorBlue(int rgb) {
        return (rgb & 0xFF) / 255.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record LightmapBakeResult(BufferedImage image, int pixelsPerTile, double bakeMs, int appliedSamples) {
    }
}

package org.main.engine;

import org.main.core.GameConfiguration;

import java.util.List;

public final class LightingResolver {
    private DungeonMap map;
    private MapLightingSettings settings = MapLightingSettings.defaultSettings();
    private List<MapLight> lights = List.of();
    private boolean globallyEnabled;
    private boolean occlusionEnabled;
    private boolean flickerEnabled;
    private int maxLights;
    private long frameMillis;
    private int activeLights;
    private int appliedLights;

    public void beginFrame(DungeonMap map, long frameMillis) {
        this.map = map;
        this.settings = map == null ? MapLightingSettings.defaultSettings() : map.getLightingSettings();
        this.lights = map == null ? List.of() : map.getLightsView();
        this.globallyEnabled = GameConfiguration.booleanValue("lighting.enabled", true)
                && settings.lightingEnabled();
        this.occlusionEnabled = GameConfiguration.booleanValue("lighting.occlusion.enabled", true);
        this.flickerEnabled = GameConfiguration.booleanValue("lighting.flicker.enabled", true);
        this.maxLights = Math.max(0, GameConfiguration.intValue("lighting.maxLights", 64));
        this.frameMillis = frameMillis;
        this.activeLights = countActiveLights();
        this.appliedLights = 0;
    }

    public LightSample sample(double worldX, double worldY, double worldZ) {
        if (!globallyEnabled || map == null) {
            return LightSample.fullBright();
        }

        double red = settings.red() * settings.ambientIntensity();
        double green = settings.green() * settings.ambientIntensity();
        double blue = settings.blue() * settings.ambientIntensity();
        int sampleTileX = clamp((int) Math.floor(worldX), 0, map.getWidth() - 1);
        int sampleTileY = clamp((int) Math.floor(worldZ), 0, map.getHeight() - 1);
        int visited = 0;

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
            if (occlusionEnabled && !hasLineOfSight(sampleTileX, sampleTileY, light.x(), light.y())) {
                continue;
            }

            double falloff = 1.0 - distance / light.radius();
            double contribution = falloff * falloff * light.intensity() * flickerMultiplier(light);
            red += colorRed(light.colorRgb()) * contribution;
            green += colorGreen(light.colorRgb()) * contribution;
            blue += colorBlue(light.colorRgb()) * contribution;
            appliedLights++;
        }

        return new LightSample(clamp(red), clamp(green), clamp(blue), activeLights, appliedLights);
    }

    public boolean fogEnabled() {
        return GameConfiguration.booleanValue("lighting.fog.enabled", true)
                && globallyEnabled
                && settings.fogEnabled()
                && settings.fogDensity() > 0.0;
    }

    public MapLightingSettings settings() {
        return settings;
    }

    public int activeLights() {
        return activeLights;
    }

    public int appliedLights() {
        return appliedLights;
    }

    private double flickerMultiplier(MapLight light) {
        if (!flickerEnabled || light.flickerAmount() <= 0.0) {
            return 1.0;
        }
        long bucket = frameMillis / 90L;
        long seed = (long) light.id().hashCode() * 1103515245L + bucket * 12345L;
        double normalized = ((seed >>> 16) & 0x7FFF) / 32767.0;
        return Math.max(0.0, 1.0 - light.flickerAmount() * 0.5 + normalized * light.flickerAmount());
    }

    private int countActiveLights() {
        int count = 0;
        for (MapLight light : lights) {
            if (light != null && light.enabled()) {
                count++;
                if (count >= maxLights) {
                    break;
                }
            }
        }
        return count;
    }

    private boolean hasLineOfSight(int fromX, int fromY, int targetX, int targetY) {
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

    public record LightSample(float red, float green, float blue, int consideredLights, int appliedLights) {
        public static LightSample fullBright() {
            return new LightSample(1.0f, 1.0f, 1.0f, 0, 0);
        }

        private LightSample(double red, double green, double blue, int consideredLights, int appliedLights) {
            this((float) red, (float) green, (float) blue, consideredLights, appliedLights);
        }
    }
}

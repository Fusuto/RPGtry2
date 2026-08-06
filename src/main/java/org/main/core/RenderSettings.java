package org.main.core;

import java.util.Locale;

/** Immutable persisted settings used by the render loop and View menu. */
public record RenderSettings(
        boolean vSync,
        FrameLimit frameLimit,
        int fieldOfViewDegrees,
        int renderDistance,
        boolean performanceOverlayVisible
) {
    public static final int MIN_FOV = 45;
    public static final int MAX_FOV = 100;
    public static final int MIN_RENDER_DISTANCE = 4;
    public static final int MAX_RENDER_DISTANCE = 32;

    public enum FrameLimit {
        DISPLAY(0, "Display"),
        FPS_60(60, "60 FPS"),
        FPS_120(120, "120 FPS"),
        FPS_144(144, "144 FPS"),
        UNCAPPED(0, "Uncapped");

        private final int framesPerSecond;
        private final String displayName;

        FrameLimit(int framesPerSecond, String displayName) {
            this.framesPerSecond = framesPerSecond;
            this.displayName = displayName;
        }

        public int framesPerSecond(int displayRefreshRate) {
            return this == DISPLAY ? Math.max(1, displayRefreshRate) : framesPerSecond;
        }

        public String displayName() {
            return displayName;
        }

        public FrameLimit next() {
            FrameLimit[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static FrameLimit parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return DISPLAY;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT)
                    .replace(' ', '_')
                    .replace("FPS", "FPS_");
            if (normalized.equals("60") || normalized.equals("FPS__60")) {
                return FPS_60;
            }
            if (normalized.equals("120") || normalized.equals("FPS__120")) {
                return FPS_120;
            }
            if (normalized.equals("144") || normalized.equals("FPS__144")) {
                return FPS_144;
            }
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return DISPLAY;
            }
        }
    }

    public static RenderSettings load() {
        return new RenderSettings(
                GameConfiguration.booleanValue("renderer.vsync.enabled", true),
                FrameLimit.parse(GameConfiguration.stringValue("renderer.frameLimit", "DISPLAY")),
                clamp(GameConfiguration.intValue("renderer.prototype.fovDegrees", 70), MIN_FOV, MAX_FOV),
                clamp(GameConfiguration.intValue("renderer.prototype.maxDepth", 12),
                        MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE),
                GameConfiguration.booleanValue("renderer.performanceOverlay.visible", false)
        );
    }

    public static void saveVSync(boolean enabled) {
        GameConfiguration.setValue("renderer.vsync.enabled", Boolean.toString(enabled));
    }

    public static void saveFrameLimit(FrameLimit limit) {
        GameConfiguration.setValue("renderer.frameLimit", (limit == null ? FrameLimit.DISPLAY : limit).name());
    }

    public static void savePerformanceOverlayVisible(boolean visible) {
        GameConfiguration.setValue("renderer.performanceOverlay.visible", Boolean.toString(visible));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

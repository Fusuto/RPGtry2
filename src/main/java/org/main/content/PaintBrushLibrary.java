package org.main.content;

import org.main.engine.EnvironmentTheme;
import org.main.engine.MapPaintData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PaintBrushLibrary {
    private static final List<PaintBrush> BRUSHES = buildBrushes();
    private static final List<Palette> PALETTES = buildPalettes();

    private PaintBrushLibrary() {
    }

    public static List<Palette> palettes() {
        return PALETTES;
    }

    public static List<PaintBrush> brushes() {
        return BRUSHES;
    }

    public static List<PaintBrush> brushesForPalette(String paletteId) {
        if (paletteId == null || paletteId.isBlank()) {
            return BRUSHES;
        }

        return BRUSHES.stream()
                .filter(brush -> brush.paletteId().equals(paletteId))
                .sorted(Comparator
                        .comparing((PaintBrush brush) -> brush.layer().name())
                        .thenComparing(PaintBrush::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static List<PaintBrush> brushesForPaletteAndLayer(String paletteId, MapPaintData.Layer layer) {
        return brushesForPalette(paletteId).stream()
                .filter(brush -> brush.layer() == layer)
                .toList();
    }

    public static PaintBrush find(String brushId) {
        if (brushId == null || brushId.isBlank()) {
            return null;
        }

        for (PaintBrush brush : BRUSHES) {
            if (brush.id().equals(brushId)) {
                return brush;
            }
        }
        return null;
    }

    public static EnvironmentTheme.TextureTheme textureForBrush(String brushId) {
        PaintBrush brush = find(brushId);
        return brush == null ? null : brush.texture();
    }

    private static List<Palette> buildPalettes() {
        List<Palette> palettes = new ArrayList<>();
        palettes.add(new Palette("", "All Palettes"));
        for (ThemeLibrary theme : ThemeLibrary.values()) {
            palettes.add(new Palette(theme.name(), theme.getDisplayName()));
        }
        return List.copyOf(palettes);
    }

    private static List<PaintBrush> buildBrushes() {
        List<PaintBrush> brushes = new ArrayList<>();
        for (ThemeLibrary theme : ThemeLibrary.values()) {
            EnvironmentTheme environmentTheme = theme.getTheme();
            brushes.add(fromTheme(theme, MapPaintData.Layer.FLOOR, "Floor", environmentTheme.floor()));
            brushes.add(fromTheme(theme, MapPaintData.Layer.WALL, "Wall", environmentTheme.wall()));
            brushes.add(fromTheme(theme, MapPaintData.Layer.DOOR, "Door", environmentTheme.door()));
            brushes.add(fromTheme(theme, MapPaintData.Layer.ROOF, "Roof", environmentTheme.wall()));
        }
        return List.copyOf(brushes);
    }

    private static PaintBrush fromTheme(
            ThemeLibrary theme,
            MapPaintData.Layer layer,
            String layerLabel,
            EnvironmentTheme.TextureTheme texture
    ) {
        String id = (theme.name() + "_" + layer.name() + "_" + texture.location() + "_" + texture.material1() + "_"
                + texture.material2() + "_" + texture.side()).toLowerCase(Locale.ROOT);
        return new PaintBrush(
                id,
                theme.getDisplayName() + " " + layerLabel,
                theme.name(),
                theme.getDisplayName(),
                layer,
                texture
        );
    }

    public record Palette(String id, String displayName) {
        public Palette {
            id = id == null ? "" : id;
            displayName = displayName == null || displayName.isBlank() ? "Palette" : displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record PaintBrush(
            String id,
            String displayName,
            String paletteId,
            String paletteName,
            MapPaintData.Layer layer,
            EnvironmentTheme.TextureTheme texture
    ) {
        public PaintBrush {
            id = id == null ? "" : id;
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            paletteId = paletteId == null ? "" : paletteId;
            paletteName = paletteName == null || paletteName.isBlank() ? "Palette" : paletteName;
            layer = layer == null ? MapPaintData.Layer.FLOOR : layer;
        }

        @Override
        public String toString() {
            return displayName + " [" + layer + "]";
        }
    }
}

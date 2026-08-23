package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.core.Library;
import org.main.engine.MapGeometryData;
import org.main.engine.MobAreaData;
import org.main.engine.MapPaintData;

import java.util.List;

record MapPrefab(
        String name,
        int width,
        int height,
        Library.TileType[][] tiles,
        int[][] themes,
        MapPaintData paintData,
        MapGeometryData geometryData,
        MobAreaData mobAreas,
        List<MapDesignLibrary.MapPlacement> placements,
        List<MapDesignLibrary.MapTrigger> triggers
) {
    MapPrefab {
        name = name == null || name.isBlank() ? "Prefab" : name;
        width = Math.max(1, width);
        height = Math.max(1, height);
        paintData = paintData == null ? MapPaintData.blank(width, height) : paintData;
        geometryData = geometryData == null ? MapGeometryData.blank(width, height) : geometryData;
        mobAreas = mobAreas == null ? MobAreaData.blank(width, height) : mobAreas;
        placements = placements == null ? List.of() : List.copyOf(placements);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + ")";
    }
}


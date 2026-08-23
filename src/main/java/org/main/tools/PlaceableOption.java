package org.main.tools;

import org.main.content.MapDesignLibrary;

record PlaceableOption(String label, MapDesignLibrary.PlacementKind kind, String id) {
    @Override
    public String toString() {
        return label;
    }
}


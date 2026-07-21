package org.main.core;

import java.util.ArrayList;
import java.util.List;

public class MiniMapState {
    private boolean miniMapUnlocked = false;
    private GameState.MiniMapMode miniMapMode = GameState.MiniMapMode.DISCOVERED;
    private boolean[][] discoveredMiniMapTiles = new boolean[0][0];
    private boolean miniMapVisible = true;
    private boolean miniMapDebugMode = false;

    public boolean isMiniMapUnlocked() {
        return miniMapUnlocked;
    }

    public void setMiniMapUnlocked(boolean miniMapUnlocked) {
        this.miniMapUnlocked = miniMapUnlocked;
    }

    public GameState.MiniMapMode getMiniMapMode() {
        return miniMapMode;
    }

    public void setMiniMapMode(GameState.MiniMapMode miniMapMode) {
        if (miniMapMode != null) {
            this.miniMapMode = miniMapMode;
        }
    }

    public boolean isMiniMapVisible() {
        return miniMapVisible;
    }

    public void setMiniMapVisible(boolean miniMapVisible) {
        this.miniMapVisible = miniMapVisible;
    }

    public boolean isMiniMapDebugMode() {
        return miniMapDebugMode;
    }

    public void setMiniMapDebugMode(boolean miniMapDebugMode) {
        this.miniMapDebugMode = miniMapDebugMode;
    }

    public boolean[][] getDiscoveredMiniMapTiles() {
        return discoveredMiniMapTiles;
    }

    public void resizeDiscoveredTiles(int width, int height) {
        if (width <= 0 || height <= 0) {
            discoveredMiniMapTiles = new boolean[0][0];
            return;
        }
        discoveredMiniMapTiles = new boolean[height][width];
    }

    public void discoverTile(int x, int y) {
        if (isTileDiscoveredBoundsValid(x, y)) {
            discoveredMiniMapTiles[y][x] = true;
        }
    }

    public boolean isTileDiscovered(int x, int y) {
        if (miniMapMode == GameState.MiniMapMode.DEBUG || miniMapDebugMode) {
            return true;
        }
        if (!isTileDiscoveredBoundsValid(x, y)) {
            return false;
        }
        return discoveredMiniMapTiles[y][x];
    }

    public List<String> getDiscoveredTileKeys() {
        List<String> keys = new ArrayList<>();
        for (int y = 0; y < discoveredMiniMapTiles.length; y++) {
            for (int x = 0; x < discoveredMiniMapTiles[y].length; x++) {
                if (discoveredMiniMapTiles[y][x]) {
                    keys.add(x + "," + y);
                }
            }
        }
        return keys;
    }

    public void setDiscoveredTileKeys(List<String> tileKeys) {
        if (tileKeys == null) {
            return;
        }
        for (String key : tileKeys) {
            String[] parts = key.split(",");
            if (parts.length == 2) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    discoverTile(x, y);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private boolean isTileDiscoveredBoundsValid(int x, int y) {
        return y >= 0 && y < discoveredMiniMapTiles.length && x >= 0 && x < discoveredMiniMapTiles[y].length;
    }
}

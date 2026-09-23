package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.content.MapDesignLibrary.*;
import org.main.core.Library;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Headless map operations shared by automation clients. Does not initialize Swing. */
public final class ConstructionKitMapService {
    private final Path root;

    public ConstructionKitMapService(Path root) throws IOException {
        this.root = root.toRealPath();
        if (!Files.isDirectory(this.root)) throw new IOException("Resource root must be a directory");
    }

    public Path resolve(String relative) throws IOException {
        Path input = Path.of(relative);
        Path result = root.resolve(input).normalize();
        if (input.isAbsolute() || !result.startsWith(root) || result.equals(root)
                || !relative.endsWith(".properties")) {
            throw new IOException("Map path must be a relative .properties file inside the resource root");
        }
        // Check existing ancestors too: lexical containment alone permits symlink escapes.
        for (Path p = result; p != null && p.startsWith(root); p = p.getParent()) {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !p.toRealPath().startsWith(root)) {
                throw new IOException("Map path escapes the resource root through a link");
            }
        }
        if (!result.startsWith(root.resolve("assets"))) {
            throw new IOException("Maps must be stored under assets/");
        }
        return result;
    }

    public MapDesign load(String path) throws IOException {
        Path source = resolve(path);
        if (!Files.isRegularFile(source)) throw new IOException("Map does not exist: " + path);
        return MapDesignLibrary.load(source);
    }

    public MapDesign create(int width, int height) {
        if (width < 3 || height < 3 || width > 80 || height > 80) {
            throw new IllegalArgumentException("Map dimensions must be between 3 and 80");
        }
        return MapDesignLibrary.createBlank(width, height, null, null);
    }

    public void setTile(MapDesign map, int x, int y, Library.TileType tile) {
        checkPosition(map, x, y);
        map.tiles()[y][x] = java.util.Objects.requireNonNull(tile);
    }

    public void place(MapDesign map, PlacementKind kind, String id, int x, int y) {
        checkPosition(map, x, y);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Placement ID is required");
        MapPlacement placement = new MapPlacement(java.util.Objects.requireNonNull(kind), id, x, y);
        if (map.placements().contains(placement)) throw new IllegalArgumentException("Placement already exists");
        map.placements().add(placement);
    }

    public List<ValidationIssue> validate(MapDesign map) {
        return MapDesignLibrary.validate(map);
    }

    /** Never truncates the destination; requires atomic replacement support from the filesystem. */
    public void save(MapDesign map, String path, boolean replace) throws IOException {
        if (validate(map).stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR)) {
            throw new IOException("Map has validation errors; nothing was saved");
        }
        Path destination = resolve(path);
        if (!replace && Files.exists(destination)) throw new FileAlreadyExistsException(path);
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".aether-map-", ".properties");
        try {
            MapDesignLibrary.save(map, temporary);
            if (replace) {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                // A non-replacing move prevents an existing map from being overwritten.
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkPosition(MapDesign map, int x, int y) {
        if (x < 0 || y < 0 || x >= map.width() || y >= map.height()) {
            throw new IllegalArgumentException("Coordinates outside map: " + x + "," + y);
        }
    }
}

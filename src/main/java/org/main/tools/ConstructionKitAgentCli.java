package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.content.MapDesignLibrary.*;
import org.main.core.Library;
import org.main.engine.ApplicationPaths;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

/** One command per process; stdout is a machine-readable Java properties response. */
public final class ConstructionKitAgentCli {
    private ConstructionKitAgentCli() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream output) {
        Properties response = new Properties();
        int status = 0;
        try {
            if (args.length == 0 || args[0].equals("help")) {
                response.setProperty("commands", "list-assets,inspect,create,set-tile,place,validate");
                response.setProperty("usage", "COMMAND --root RESOURCE_ROOT --map assets/.../map.properties [options]");
                response.setProperty("create.options", "--width N --height N [--dry-run true]");
                response.setProperty("set-tile.options", "--x N --y N --tile ENUM [--dry-run true]");
                response.setProperty("place.options", "--x N --y N --kind ENUM --id ID [--dry-run true]");
                response.setProperty("tile.values", Arrays.toString(Library.TileType.values()));
                response.setProperty("kind.values", Arrays.toString(PlacementKind.values()));
            } else {
                String command = args[0];
                Set<String> allowed = new HashSet<>(Set.of("root", "map"));
                switch (command) {
                    case "create" -> allowed.addAll(Set.of("width", "height", "dry-run"));
                    case "set-tile" -> allowed.addAll(Set.of("x", "y", "tile", "dry-run"));
                    case "place" -> allowed.addAll(Set.of("x", "y", "kind", "id", "dry-run"));
                    case "inspect", "validate", "list-assets" -> { }
                    default -> throw new IllegalArgumentException("Unknown command: " + command);
                }
                Map<String, String> options = new HashMap<>();
                for (int i = 1; i < args.length; i += 2) {
                    if (!args[i].startsWith("--") || i + 1 >= args.length) {
                        throw new IllegalArgumentException("Options require --name value pairs");
                    }
                    String name = args[i].substring(2);
                    if (!allowed.contains(name) || options.putIfAbsent(name, args[i + 1]) != null) {
                        throw new IllegalArgumentException("Unknown or repeated option: " + name);
                    }
                }
                String dry = options.getOrDefault("dry-run", "false");
                if (!dry.equals("true") && !dry.equals("false")) {
                    throw new IllegalArgumentException("dry-run must be true or false");
                }
                Path root = Path.of(required(options, "root")).toRealPath();
                // Configure the same asset/catalog resolution used by the Construction Kit.
                if (Files.isRegularFile(root.resolve("aether-pack.properties"))) {
                    System.clearProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);
                    System.setProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY, root.toString());
                } else {
                    System.clearProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
                    System.setProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY, root.toString());
                }
                if (command.equals("list-assets")) {
                    List<String> assets;
                    Path assetRoot = root.resolve("assets");
                    if (Files.isDirectory(assetRoot)) {
                        try (var paths = Files.walk(assetRoot)) {
                            assets = paths.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                                    .map(root::relativize).map(p -> p.toString().replace('\\', '/')).sorted().toList();
                        }
                    } else assets = List.of();
                    response.setProperty("asset.count", String.valueOf(assets.size()));
                    for (int i = 0; i < assets.size(); i++) response.setProperty("asset." + i, assets.get(i));
                    response.setProperty("protocol.version", "1");
                    response.setProperty("ok", "true");
                    response.store(output, "Aether Construction Kit agent response");
                    return 0;
                }
                String path = required(options, "map");
                ConstructionKitMapService service = new ConstructionKitMapService(root);
                service.resolve(path);
                MapDesign map = command.equals("create")
                        ? service.create(number(options, "width"), number(options, "height")) : service.load(path);
                if (command.equals("set-tile")) {
                    service.setTile(map, number(options, "x"), number(options, "y"),
                            Library.TileType.valueOf(required(options, "tile")));
                } else if (command.equals("place")) {
                    service.place(map, PlacementKind.valueOf(required(options, "kind")),
                            required(options, "id"), number(options, "x"), number(options, "y"));
                }
                List<ValidationIssue> issues = service.validate(map);
                response.setProperty("issue.count", String.valueOf(issues.size()));
                for (int i = 0; i < issues.size(); i++) {
                    response.setProperty("issue." + i + ".severity", issues.get(i).severity().name());
                    response.setProperty("issue." + i + ".message", issues.get(i).message());
                }
                boolean valid = issues.stream().noneMatch(i -> i.severity() == ValidationSeverity.ERROR);
                response.setProperty("valid", String.valueOf(valid));
                boolean mutation = Set.of("create", "set-tile", "place").contains(command);
                boolean saved = mutation && valid && !Boolean.parseBoolean(dry);
                if (saved) service.save(map, path, !command.equals("create"));
                response.setProperty("saved", String.valueOf(saved));
                response.setProperty("width", String.valueOf(map.width()));
                response.setProperty("height", String.valueOf(map.height()));
                response.setProperty("spawn.x", String.valueOf(map.spawnX()));
                response.setProperty("spawn.y", String.valueOf(map.spawnY()));
                response.setProperty("placement.count", String.valueOf(map.placements().size()));
                if (command.equals("inspect")) {
                    for (int y = 0; y < map.height(); y++) {
                        response.setProperty("tiles." + y, String.join(",",
                                Arrays.stream(map.tiles()[y]).map(Enum::name).toList()));
                    }
                    for (int i = 0; i < map.placements().size(); i++) {
                        MapPlacement p = map.placements().get(i);
                        response.setProperty("placement." + i + ".kind", p.kind().name());
                        response.setProperty("placement." + i + ".id", p.id());
                        response.setProperty("placement." + i + ".x", String.valueOf(p.x()));
                        response.setProperty("placement." + i + ".y", String.valueOf(p.y()));
                    }
                }
                if (!valid) status = 2;
            }
        } catch (Exception | LinkageError error) {
            status = 1;
            response.setProperty("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        response.setProperty("protocol.version", "1");
        response.setProperty("ok", String.valueOf(status == 0));
        try { response.store(output, "Aether Construction Kit agent response"); }
        catch (java.io.IOException error) { return 1; }
        return status;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing --" + key);
        return value;
    }

    private static int number(Map<String, String> options, String key) {
        return Integer.parseInt(required(options, key));
    }
}

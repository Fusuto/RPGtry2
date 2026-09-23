# Construction Kit agent interface (v1)

This first version provides headless map authoring using the same MapDesignLibrary
serialization and validation as the visual Construction Kit. No Swing window is opened.
It does not yet synchronize with an open editor; close a map in the editor before
changing it through the CLI, then reopen it. Run one writing process at a time.

Compile with `mvn -DskipTests compile`, then from PowerShell:

```powershell
./dev/construction-kit-agent.ps1 help
./dev/construction-kit-agent.ps1 list-assets --root C:/path/to/resources
./dev/construction-kit-agent.ps1 create --root C:/path/to/resources --map assets/editor/maps/example.properties --width 12 --height 10
./dev/construction-kit-agent.ps1 inspect --root C:/path/to/resources --map assets/editor/maps/example.properties
./dev/construction-kit-agent.ps1 set-tile --root C:/path/to/resources --map assets/editor/maps/example.properties --x 3 --y 4 --tile WALL --dry-run true
./dev/construction-kit-agent.ps1 place --root C:/path/to/resources --map assets/editor/maps/example.properties --kind ENEMY --id existing_mob_id --x 5 --y 5 --dry-run true
./dev/construction-kit-agent.ps1 validate --root C:/path/to/resources --map assets/editor/maps/example.properties
```

Use an existing resource root or Construction Kit project directory. For a user
pack, use `assets/packs/<namespace>/editor/maps/example.properties`. The root is
explicit: the tool does not silently choose your source tree. Asset listing shows
files in that root, not assets from every installed dependency. Entity IDs must
already exist in content available to the application's asset resolver.

Requests use strict `--name value` pairs. Unknown or duplicate options are errors.
Coordinates are zero-based; dimensions are 3–80, matching the editor. Enum names
are case-sensitive and are listed by `help`. Creation refuses existing files.
Mutations validate before writing; errors prevent saving, warnings do not.
`--dry-run true` validates a proposed edit without saving. Ordinary edits use a
temporary file and atomic replacement, retaining the original if serialization
fails. This is not a multi-file transaction or an undo history.

Responses are Java properties on stdout (including Java properties escaping),
with `protocol.version`, `ok`, and command-specific fields. Use a properties
parser, not splitting lines at every equals sign. Exit codes: 0 success,
1 request/IO failure, 2 validation errors. Validation returns indexed issue
messages and severities. Inspection returns tile rows, spawn, and placements.
This dependency-free protocol is the initial transport; a JSON/MCP adapter can
wrap the service later without coupling editing logic to that transport.

Current scope excludes content-definition authoring, prefab placement, world
operations, pack export, batch transactions, and live editor synchronization.
`ConstructionKitMapService` owns reusable map operations; the existing Swing
handlers have not yet been migrated to this service.

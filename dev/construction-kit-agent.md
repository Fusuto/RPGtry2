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

Current scope excludes general content-definition authoring, prefab placement, world
operations, pack export, batch transactions, and live editor synchronization.
`ConstructionKitMapService` owns reusable map operations; the existing Swing
handlers have not yet been migrated to this service.

## Weapon authoring test: iron mace

`create-weapon` now supports source resource roots (not installed user packs yet).
It inherits economy, smithing requirements, and equipment skill from an existing
weapon, uses the requested weapon type/material, and calculates a first-person
socket from the model's grip marker and the current arm rig. Existing IDs are
rejected. The item catalog is reloaded and compared before publication.

```powershell
./dev/construction-kit-agent.ps1 create-weapon --root ./src/main/resources --template IRON_SWORD --id custom_item_iron_mace --name 'Iron Mace' --weapon-type MACE --material iron --model assets/3D/weapons/mace/gritty_mace.glb --icon assets/3D/weapons/mace/mace_icon.png --dry-run true
./dev/construction-kit-agent.ps1 place --root ./src/main/resources --map assets/editor/maps/Bryan_TestMap.properties --kind ITEM --id custom_item_iron_mace --x 11 --y 14 --allow-existing-errors true
```

These exact mutations have already been applied; rerunning creation/placement
will reject duplicates. Omit `--dry-run true` when creating a new weapon ID.
For another tier, use a new ID/name/material and reuse the same GLB and icon.
Review inherited smithing level/value when balancing additional tiers.

`--allow-existing-errors true` is supported only for additive placement. It
compares error messages and multiplicities before/after, rejects new errors,
and reports `existingErrorsAccepted=true` and `valid=false` when old errors remain.
Weapon creation similarly refuses new errors in the shared content catalog.

The mace was built locally in Blender after Meshy's free-plan API rejected
generation. It has 2,492 triangles and a `FP_GRIP_PRIMARY` marker. Source and
preview: `asset-source/weapons/mace/`. Rebuild with Blender using
`--background --python dev/create-gritty-mace.py`.

Material names `tier_metal` or `tier_metal.*` opt a GLB mesh into gear-material
tinting for equipped weapons and live model icons. Other meshes share their
original appearance. Optional `icon.tint-mask.png` beside `icon.png` supplies a
white metal / black grip mask for ground sprites and bitmap fallback icons.

Known pre-existing test-map errors: missing BURNT_FISH and copper-dagger item
definitions, plus four incompatible kobold animation bindings. This edit leaves
those errors unchanged. The mace uses the existing MACE animation default.

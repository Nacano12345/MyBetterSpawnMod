# Proto781M's Better Spawn

A NeoForge 1.21 mod that replaces the vanilla spawn algorithm with a data-driven,
per-dimension spawn system.

## Features

- **JSON spawn templates** in `data/<namespace>/spawn_rules/` (a built-in template for the
  three vanilla dimensions is always available; the Nether defaults to `portal_type`).
- **Six spawn methods**:
  | id | purpose |
  |---|---|
  | `default` | Overworld-style ring search for the nearest safe land. |
  | `top` | Nether-style search downwards from the ceiling. |
  | `fixed` | Use an exact `x/y/z`, optionally with a safety check. |
  | `portal_type` | Finds a flat, safe Nether site that fits a 2×3 portal interior; optionally builds a cleared platform if no natural site exists. It only chooses the site, it does not place a portal. |
  | `cave` | Scans for a safe underground cavity with a solid floor and a low roof; works in dimensions without sky (Nether, custom cave dimensions) and in the Overworld underground. |
  | `floating_island` | Wider ring search with a configurable minimum radius. |
  | `water_world` | Treats fluids as air and finds solid ground, preferring dry land. |
  | `platform` | Builds a platform as the final fallback. |
- **Three-layer respawn data**:
  1. per-dimension world spawn (`SavedData`, mirrored to `level.dat` for the overworld);
  2. per-player, per-dimension world spawn (player persistent data);
  3. per-player command / bed / anchor spawn slots.
- Respawn priority is configurable:
  `command > bed/anchor > personal dimension spawn > dimension world spawn`
  or `bed/anchor > command > ...`.
- Native `/setworldspawn` and `/spawnpoint` keep working: their changes are mirrored into
  the mod's data automatically.
- `/gotodim` sandbox command for testing templates without touching the world.

## Commands

All commands are under `/betterspawn`:

```text
/betterspawn worldspawn global [<pos> | <dimension>] [<pos>] [<angle>]
/betterspawn worldspawn player <targets> [<pos> | <dimension>] [<pos>] [<angle>]
/betterspawn worldspawn clear [<dimension>] [<targets>]
/betterspawn worldspawn query [<dimension>] [<target>]
/betterspawn worldspawn regenerate [<dimension>]
/betterspawn worldspawn list
/betterspawn worldspawn reload

/betterspawn spawnpoint set <targets> [<pos>] [<angle>] [<dimension>]
/betterspawn spawnpoint bed <targets> [<pos>] [<angle>] [<dimension>]
/betterspawn spawnpoint anchor <targets> [<pos>] [<angle>] [<dimension>]
/betterspawn spawnpoint clear <targets> [command|bed|anchor|all]
/betterspawn spawnpoint query <target>

/gotodim [<dimension>] [simulate|teleport|verify] [--method <id>] [--params <json>]
         [--seed <n>] [--repeat <n>] [--timeout <ticks>] [--verbose] [--all]
```

`/gotodim` is also available as `/betterspawn gotodim`.
Omitted arguments default to the current dimension / the player's feet position / the
player's current yaw.

## Template format

```json
{
  "dimension": "minecraft:overworld",
  "method": "default",
  "parameters": {
    "center_x": 0,
    "center_z": 0,
    "max_radius": 512,
    "ring_step": 64,
    "max_chunk_loads": 128
  },
  "fallback": "platform",
  "fallback_parameters": {
    "x": 0, "y": 64, "z": 0,
    "size": 3,
    "material": "minecraft:stone"
  }
}
```

The `dimension` field is authoritative; the file path is only used for organization.
Rules from datapacks override the built-in defaults.

### Experimental: spawn dimension in the create-world screen

When `experimental.enableSpawnDimensionSelector` is enabled (default), the singleplayer/LAN
create-world screen shows a **Spawn dimension** button that cycles Overworld / Nether / End.
The choice is stored in the world's `betterspawn_world_spawn` data. On the **first join**, new
players are teleported once to that dimension's default spawn; existing players keep their
position. Dedicated servers do not have this screen and default to the Overworld; the same
setting can be changed with:

```text
/betterspawn worldspawn spawn_dimension [<dimension>]
```

This is experimental and singleplayer/LAN-host oriented.

### Cave / no-sky dimensions

`cave` scans for a safe standing point with a solid floor and a roof within
`roof_scan` blocks. It does **not** use the sky or the surface heightmap, so it also
works in dimensions with a ceiling (Nether) and underground.

```json
{
  "dimension": "your:no_sky_dimension",
  "method": "cave",
  "parameters": {
    "center_x": 0,
    "center_z": 0,
    "min_y": 32,
    "max_y": 120,
    "search_radius": 128,
    "ring_step": 32,
    "min_air_height": 2,
    "roof_scan": 8,
    "avoid_lava": true,
    "max_chunk_loads": 64
  },
  "fallback": "platform",
  "fallback_parameters": {
    "x": 0, "y": 64, "z": 0,
    "size": 5,
    "material": "minecraft:netherrack"
  }
}
```

Put this in `data/<your_pack>/spawn_rules/<dimension_path>.json`, or copy
[`docs/cave_template.json`](docs/cave_template.json). For testing a method without changing a
template, use `/betterspawn gotodim <dimension> simulate --method cave --verbose`.

## Important implementation notes

- `Level#getHeight` already returns the **feet** Y (top solid block + 1). Do not add 1 again.
- `getHeight` returns `minBuildHeight` for unloaded chunks. Spawn resolution therefore runs at
  `LevelEvent.CreateSpawnPosition` (new world) or `ServerStartedEvent` (existing world), and
  `TerrainScanner` force-loads chunks before reading heightmaps.
- Chunk generation is bounded by `search.maxChunkLoads` (default 128) and each method's
  `max_chunk_loads` parameter. This keeps first-join work predictable.
- Old saves with data version `0` (written by early WIP builds) are migrated automatically.
  If the overworld `level.dat` spawn differs from the old mod data (for example after a vanilla
  `/setworldspawn`), that choice is preserved; the other dimensions are re-resolved.

## Diagnostics

Detailed diagnostics are available but **off by default**. Enable them in
`run/config/betterspawn-common.toml`:

```toml
[debug]
    enableDiagnosticLogs = true
```

When enabled, logs prefixed with `[diag]` include the respawn chain, personal-spawn
writes, initial spawn-dimension teleports, `gotodim` results, `verify` checks, spawn-slot
recording, and create-world screen injection. Restart the game/server after changing it.

## Build

Requires JDK 21. The Gradle toolchain downloads it if needed.

```bash
./gradlew build        # build/libs/betterspawn-1.0.0.jar
./gradlew runClient
./gradlew runServer
```

## Project layout

```text
src/main/java/com/proto781m/betterspawn/
    api/                 method interface, registry, context, result
    command/             worldspawn / spawnpoint / gotodim
    data/                WorldSpawnData, PlayerSpawnData, slots
    event/               command registration, respawn interception, spawn-slot mirroring
    logic/               priority resolver, dimension initializer, verify tasks
    method/              the six spawn methods
    resolver/            method dispatcher, safety checks, terrain scanner
    template/            JSON loading and built-in defaults
    util/                platform builder, safe-position finder, adapters
src/main/resources/data/betterspawn/spawn_rules/minecraft/   built-in templates
```

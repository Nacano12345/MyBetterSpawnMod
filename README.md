# Proto781M's Better Spawn

A NeoForge 1.21 mod that replaces the vanilla spawn algorithm with a data-driven,
per-dimension spawn system.

## Features

- **JSON spawn templates** in `data/<namespace>/spawn_rules/` (a built-in template for the
  three vanilla dimensions is always available).
- **Six spawn methods**:
  | id | purpose |
  |---|---|
  | `default` | Overworld-style ring search for the nearest safe land. |
  | `top` | Nether-style search downwards from the ceiling. |
  | `fixed` | Use an exact `x/y/z`, optionally with a safety check. |
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

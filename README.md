# Proto781M's Better Spawn

**Proto781M's Better Spawn** is a NeoForge 1.21 mod that replaces the vanilla spawn
algorithm with a data-driven, per-dimension spawn and respawn system.

It is built around three ideas:

- **Data-driven spawn templates** — each dimension can define how its default spawn is
  found, using a JSON rule with a selectable search method and a fallback chain.
- **A layered respawn system** — per-dimension world spawns, per-player world spawns, and
  separate command / bed / anchor spawn slots, with a configurable priority.
- **Testable search methods** — eight built-in methods, including overworld land search,
  Nether portal-site search, cave search, floating-island search, water-world search, and
  a platform fallback, plus a sandbox command for testing rules without committing them.

## Documentation

Usage, commands, template reference, configuration, diagnostics, and build instructions are
kept in the **[Wiki](https://github.com/Nacano12345/MyBetterSpawnMod/wiki)**:

- [Commands](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Commands)
- [Spawn templates](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Spawn-Templates)
- [Spawn methods](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Spawn-Methods)
- [Configuration](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Configuration)
- [Experimental spawn dimension](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Spawn-Dimension)
- [Diagnostics](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Diagnostics)
- [Building from source](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Building)
- [Architecture](https://github.com/Nacano12345/MyBetterSpawnMod/wiki/Architecture)

## Requirements

- Minecraft 1.21
- NeoForge 21.0.167 or newer
- Java 21

## Status

Source is available and builds as `betterspawn-1.0.0.jar`. Some features are experimental;
see the Wiki for details and limitations.

## License

All Rights Reserved.

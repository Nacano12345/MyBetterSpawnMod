# BetterSpawn

**Proto781M's Better Spawn** — a NeoForge mod for Minecraft 1.21 that makes the spawn point more useful and flexible.

This repository currently contains a clean mod shell (skeleton) generated from the official
[NeoForge MDK](https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle) for Minecraft 1.21.
Feature code is not implemented yet.

## Project info

| Property     | Value                                   |
|--------------|-----------------------------------------|
| Mod id       | `betterspawn`                           |
| Display name | Proto781M's Better Spawn                |
| Loader       | NeoForge                                |
| Minecraft    | 1.21                                    |
| Java         | 21                                      |
| Group        | `com.proto781m.betterspawn`             |

## Getting started

Requires JDK 21 (the Gradle toolchain will download it automatically if missing).

```bash
./gradlew build          # build the mod jar into build/libs
./gradlew runClient      # launch a client with the mod
./gradlew runServer      # launch a dedicated server with the mod
./gradlew runData        # run data generators
```

## Project layout

```
src/main/java/com/proto781m/betterspawn/
    BetterSpawn.java     # main mod class (@Mod)
    Config.java          # common config spec
src/main/resources/assets/betterspawn/lang/en_us.json
src/main/templates/META-INF/neoforge.mods.toml   # metadata template (filled by Gradle)
```

## Notes

- `neoforge.mods.toml` lives under `src/main/templates`; Gradle expands the `${...}` placeholders
  into `build/generated/sources/modMetadata`.
- See the NeoForged docs: https://docs.neoforged.net/

# Changelog

## 1.2.0

- Added `general.commandSpawnMode` with `DEFAULT`, `BED_ANCHOR`, and `WORLDSPAWN`.
- Added experimental `/spawnpoint` and `/setworldspawn` overrides behind
  `experimental.overrideVanillaCommands`; vanilla syntax is preserved and an optional
  trailing dimension is accepted.
- Added `/betterspawn offline` for editing offline player dimension spawns and
  command/bed/anchor slots directly in `playerdata`.
- Added player suggestions based on offline player data containing BetterSpawn records.
- Refactored the project into shared `common` sources and version subprojects.
- Releases now publish 1.21.1, 1.21.4, and 1.21.11 jars in one GitHub Release.

## 1.1.0

- Added combined multi-version release metadata.
- Added the PolyForm Noncommercial License 1.0.0 and source-available packaging.
- Added the mod logo and README logo variants.
- Added shared command builders and ported the command layer to 1.21.1, 1.21.4, and
  1.21.11.

## 1.0.1

- Fixed `/gotodim` probing around the current global spawn.
- Added template parameter `allow_position_overwrite`.
- Updated vanilla templates to opt in to global spawn position overwrite.

## 1.0.0

- Initial release with data-driven spawn templates, layered respawn data, eight spawn
  methods, `/gotodim`, and the experimental spawn-dimension selector.

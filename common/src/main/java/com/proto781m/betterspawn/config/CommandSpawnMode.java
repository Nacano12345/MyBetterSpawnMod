package com.proto781m.betterspawn.config;

/**
 * How a command spawn point is converted into an actual respawn position.
 */
public enum CommandSpawnMode {
    /** Respawn exactly at the command point, matching vanilla {@code /spawnpoint}. */
    DEFAULT,
    /** Search a small safe area around the command point, like a bed or respawn anchor. */
    BED_ANCHOR,
    /** Run the dimension's world-spawn template search centered on the command point. */
    WORLDSPAWN
}

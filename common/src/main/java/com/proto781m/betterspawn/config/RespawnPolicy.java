package com.proto781m.betterspawn.config;

/** Respawn priority policy: whether the command spawn or the bed/anchor spawn wins. */
public enum RespawnPolicy {
    /** Command spawn ({@code /spawnpoint set}) takes priority over bed/anchor. Recommended default. */
    COMMAND_OVER_BED,
    /** Bed/anchor spawn takes priority over the command spawn. */
    BED_OVER_COMMAND
}

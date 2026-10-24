package com.proto781m.betterspawn.config;

/** Fallback behavior when a player has no personal respawn point. */
public enum NoPersonalSpawnBehavior {
    /** Return to the Overworld global spawn (vanilla-compatible behavior). */
    BACK_TO_OVERWORLD,
    /** Stay in the death dimension's player or global spawn. */
    STAY_IN_DEATH_DIMENSION
}

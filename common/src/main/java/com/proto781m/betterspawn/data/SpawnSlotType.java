package com.proto781m.betterspawn.data;

/** Player respawn slot types. */
public enum SpawnSlotType {
    /** Command spawn ({@code /spawnpoint set}): exact standing position, independent of blocks. */
    COMMAND,
    /** Bed spawn: stores the bed block position and validates the block on respawn. */
    BED,
    /** Respawn anchor spawn: stores the anchor block position and validates charges and
     * dimension on respawn. */
    ANCHOR
}

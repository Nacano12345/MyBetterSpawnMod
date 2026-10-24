package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A single player respawn slot.
 *
 * <p>The meaning of {@code pos} depends on the slot type:
 * <ul>
 *   <li>{@link SpawnSlotType#COMMAND}: exact standing position;</li>
 *   <li>{@link SpawnSlotType#BED} / {@link SpawnSlotType#ANCHOR}: anchor block position;
 *       a safe standing position is resolved around it on respawn.</li>
 * </ul>
 *
 * @param valid runtime validation result (bed still present, anchor charged, etc.);
 *              the resolution chain uses it to decide whether to accept the slot
 */
public record SpawnSlot(
        SpawnSlotType type,
        ResourceKey<Level> dimension,
        BlockPos pos,
        float angle,
        boolean valid
) {

    /** Creates a slot marked valid. */
    public static SpawnSlot valid(SpawnSlotType type, ResourceKey<Level> dimension, BlockPos pos, float angle) {
        return new SpawnSlot(type, dimension, pos, angle, true);
    }

}

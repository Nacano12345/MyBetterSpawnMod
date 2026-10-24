package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A spawn point: position, dimension, and facing angle. */
public record SpawnPoint(BlockPos pos, ResourceKey<Level> dimension, float angle) {
}

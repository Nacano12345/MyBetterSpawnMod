package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 一个出生点：坐标 + 维度 + 朝向角度。 */
public record SpawnPoint(BlockPos pos, ResourceKey<Level> dimension, float angle) {
}

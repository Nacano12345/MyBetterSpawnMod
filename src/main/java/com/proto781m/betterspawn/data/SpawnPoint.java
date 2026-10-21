package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 一个出生点：坐标 + 维度 + 朝向角度。 */
public record SpawnPoint(BlockPos pos, ResourceKey<Level> dimension, float angle) {
    /** 兼容旧代码：仅坐标+角度，维度默认为主世界。 */
    public SpawnPoint(BlockPos pos, float angle) {
        this(pos, Level.OVERWORLD, angle);
    }
}

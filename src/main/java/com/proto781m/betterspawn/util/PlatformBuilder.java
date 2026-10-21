package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 保底平台生成器：当所有搜索方法都找不到有效位置时，在指定坐标直接搭建一个平台。
 *
 * <p>平台是一个以 {@code center} 为中心、边长 {@code size} 的正方形，材质由参数指定。
 * 生成后平台上方会留出至少 2 格空气，保证玩家可以站立。
 */
public final class PlatformBuilder {

    private PlatformBuilder() {
    }

    /**
     * 在 {@code center} 处搭建平台。
     *
     * @param center 平台中心所在的方块位置（即地面层）
     * @param size   平台边长（建议 3 或 5）
     * @param state  平台方块的 BlockState
     */
    public static void build(ServerLevel level, BlockPos center, int size, BlockState state) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), state);
            }
        }
        // 清理平台上方的遮挡物，保证 2 格站立空间
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos above = center.offset(dx, 1, dz);
                if (!level.getBlockState(above).isAir()) {
                    level.removeBlock(above, false);
                }
                BlockPos above2 = center.offset(dx, 2, dz);
                if (!level.getBlockState(above2).isAir()) {
                    level.removeBlock(above2, false);
                }
            }
        }
    }

    /** 生成 3x3 石质平台（默认保底方案）。 */
    public static void buildDefault(ServerLevel level, BlockPos center) {
        build(level, center, 3, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    }
}

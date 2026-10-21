package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 平台生成器：在指定坐标搭建一个平台，并清理上方空间。
 */
public final class PlatformBuilder {

    private PlatformBuilder() {
    }

    /** 解析方块 id；无效或空气时回退到石头。 */
    public static BlockState resolveState(String materialId) {
        try {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(materialId));
            if (block == null || block == Blocks.AIR) {
                return Blocks.STONE.defaultBlockState();
            }
            return block.defaultBlockState();
        } catch (Exception ex) {
            return Blocks.STONE.defaultBlockState();
        }
    }

    /** 在 {@code center} 处搭建平台，默认清理上方 2 格。 */
    public static void build(ServerLevel level, BlockPos center, int size, BlockState state) {
        build(level, center, size, state, 2);
    }

    /**
     * 在 {@code center} 处搭建平台，并清理上方 {@code clearHeight} 格。
     *
     * @param center      平台中心地面层
     * @param size        平台边长（建议奇数）
     * @param state       平台方块
     * @param clearHeight 平台上方需要保留的空气高度
     */
    public static void build(ServerLevel level, BlockPos center, int size, BlockState state, int clearHeight) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), state);
            }
        }

        // 清理平台上方的遮挡物，保证玩家/传送门有足够净空
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 1; dy <= clearHeight; dy++) {
                    BlockPos above = center.offset(dx, dy, dz);
                    if (!level.getBlockState(above).isAir()) {
                        level.removeBlock(above, false);
                    }
                }
            }
        }
    }

    /** 生成 3x3 石质平台（默认保底方案）。 */
    public static void buildDefault(ServerLevel level, BlockPos center) {
        build(level, center, 3, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    }
}

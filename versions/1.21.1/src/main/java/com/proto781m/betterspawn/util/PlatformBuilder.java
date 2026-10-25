package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Platform builder: creates a platform at the given coordinates and clears space above it.
 */
public final class PlatformBuilder {

    private PlatformBuilder() {
    }

    /** Resolves a block ID, falling back to stone for invalid IDs or air. */
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

    /** Builds a platform at {@code center}, clearing two blocks above by default. */
    public static void build(ServerLevel level, BlockPos center, int size, BlockState state) {
        build(level, center, size, state, 2);
    }

    /**
     * Builds a platform at {@code center} and clears {@code clearHeight} blocks above it.
     *
     * @param center      platform center ground layer
     * @param size        platform side length (odd values are recommended)
     * @param state       platform block
     * @param clearHeight air height to keep above the platform
     */
    public static void build(ServerLevel level, BlockPos center, int size, BlockState state, int clearHeight) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), state);
            }
        }

        // Clear obstructions above the platform so players/portals have enough headroom.
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

}

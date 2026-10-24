package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utility for checking safe spawn positions.
 *
 * <p>Every spawn method should run this check before returning coordinates so players
 * are not stuck in blocks, dropped into lava or water, or left over the void.
 *
 * <p>Rules:
 * <ul>
 *   <li>the block below the feet must be a solid, non-fluid block suitable for standing;</li>
 *   <li>the feet and head blocks must be air or replaceable (grass, flowers, etc.);</li>
 *   <li>the feet and head blocks must not contain fluid (water or lava).</li>
 * </ul>
 */
public final class SafePosFinder {

    private SafePosFinder() {
    }

    /** Returns whether {@code ground} is a valid standing surface (solid top and not fluid). */
    public static boolean isSafeGround(ServerLevel level, BlockPos ground) {
        BlockState state = level.getBlockState(ground);
        return state.isFaceSturdy(level, ground, Direction.UP) && state.getFluidState().isEmpty();
    }

    /** Returns whether {@code pos} is a valid player standing position (feet position). */
    public static boolean isSafeStandingPosition(ServerLevel level, BlockPos pos) {
        if (!isSafeGround(level, pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    /** Returns whether a block is passable (air or replaceable, such as grass, flowers, or snow). */
    public static boolean isPassable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * Searches within the given radius around {@code center} for the first safe standing
     * position. It checks small vertical offsets first, then expands by Manhattan
     * distance. Useful for correcting fixed-position methods.
     *
     * @return the safe position, or {@code null} if none is found
     */
    public static BlockPos findSafeAround(ServerLevel level, BlockPos center, int maxRadius) {
        if (isSafeStandingPosition(level, center)) {
            return center;
        }
        // First check small vertical offsets around the center.
        for (int dy = 1; dy <= 4; dy++) {
            if (isSafeStandingPosition(level, center.above(dy))) {
                return center.above(dy);
            }
            if (isSafeStandingPosition(level, center.below(dy))) {
                return center.below(dy);
            }
        }
        // Then search outward by radius.
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue; // Only check the outer ring
                    }
                    BlockPos candidate = center.offset(dx, 0, dz);
                    if (isSafeStandingPosition(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}

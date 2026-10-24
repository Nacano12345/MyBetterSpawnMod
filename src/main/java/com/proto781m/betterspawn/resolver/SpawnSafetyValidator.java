package com.proto781m.betterspawn.resolver;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.proto781m.betterspawn.util.SafePosFinder;

/**
 * Spawn safety validator with shared checks and warnings.
 *
 * <p>Every method should call {@link #validate} as a final check so players are never
 * placed in lava, inside blocks, or over the void.
 */
public final class SpawnSafetyValidator {

    private SpawnSafetyValidator() {
    }

    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        return SafePosFinder.isSafeStandingPosition(level, pos);
    }

    /** Validates a spawn position and returns warnings; an empty list means no warnings. */
    public static List<String> validate(ServerLevel level, BlockPos pos) {
        List<String> warnings = new ArrayList<>();
        if (pos == null) {
            warnings.add("Position is null");
            return warnings;
        }
        if (!SafePosFinder.isSafeGround(level, pos.below())) {
            warnings.add("Ground is not sturdy or is fluid");
        }
        if (!SafePosFinder.isPassable(level.getBlockState(pos))) {
            warnings.add("Feet position is occupied");
        }
        if (!SafePosFinder.isPassable(level.getBlockState(pos.above()))) {
            warnings.add("Head position is occupied");
        }
        // Warn if a floating block above the head blocks the view (advisory, not blocking).
        int blockedAbove = 0;
        for (int dy = 1; dy <= 4; dy++) {
            if (!SafePosFinder.isPassable(level.getBlockState(pos.above(dy)))) {
                blockedAbove++;
            }
        }
        if (blockedAbove > 0) {
            warnings.add(blockedAbove + " blocks above may obstruct the view");
        }
        return warnings;
    }
}

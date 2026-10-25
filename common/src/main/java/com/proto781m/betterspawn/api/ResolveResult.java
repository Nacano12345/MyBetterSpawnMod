package com.proto781m.betterspawn.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Result of a spawn-point resolution.
 *
 * @param pos              resolved spawn position (feet coordinates); may be {@code null} on failure
 * @param angle            facing angle
 * @param usedMethod       ID of the method that produced the result
 * @param fallbackUsed     whether a fallback method was used
 * @param elapsedMillis    resolution time in milliseconds
 * @param samples          number of sampled positions
 * @param maxRadiusReached maximum search radius reached
 * @param status           resolution status
 * @param warnings         warnings, such as a floating block above the spawn
 */
public record ResolveResult(
        BlockPos pos,
        ResourceKey<Level> dimension,
        float angle,
        String usedMethod,
        boolean fallbackUsed,
        long elapsedMillis,
        int samples,
        int maxRadiusReached,
        Status status,
        List<String> warnings
) {

    public enum Status {
        /** A safe spawn point was found. */
        OK,
        /** The search timed out. */
        TIMEOUT,
        /** A position was found but failed safety validation. */
        UNSAFE,
        /** A fallback method found a position. */
        FALLBACK,
        /** No position was found. */
        EMPTY
    }

    public static ResolveResult empty(String method, Status status) {
        return new ResolveResult(null, null, 0.0F, method, false, 0L, 0, 0, status, List.of());
    }

}

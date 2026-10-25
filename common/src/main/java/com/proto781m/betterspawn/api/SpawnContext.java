package com.proto781m.betterspawn.api;

import com.proto781m.betterspawn.template.SpawnRule;
import net.minecraft.server.level.ServerLevel;

/**
 * Context for resolving a spawn point.
 *
 * @param level          target dimension
 * @param rule           JSON template rule for the dimension
 * @param seed           random seed ({@code /gotodim --seed} can override it)
 * @param simulate       {@code true} for sandbox mode ({@code /gotodim simulate});
 *                       no persistent data may be written
 * @param deadlineMillis deadline timestamp in milliseconds; returns
 *                       {@link ResolveResult.Status#TIMEOUT} when exceeded
 * @param trace          diagnostic search trace
 */
public record SpawnContext(
        ServerLevel level,
        SpawnRule rule,
        long seed,
        boolean simulate,
        long deadlineMillis,
        SearchTrace trace
) {
    public boolean timedOut() {
        return System.currentTimeMillis() > deadlineMillis;
    }
}

package com.proto781m.betterspawn.api;

/**
 * Interface for spawn-point calculation methods.
 *
 * <p>A method only calculates coordinates; it does not persist them. The caller
 * ({@code SpawnMethodResolver} or {@code DimensionSpawnInitializer}) decides whether
 * to store the result.
 */
public interface SpawnMethod {

    /** Method ID. Must match the {@code method} field in the JSON template and be lowercase. */
    String id();

    /**
     * Resolves a spawn point.
     *
     * @param ctx resolution context
     * @return resolution result; may be {@link ResolveResult.Status#UNSAFE},
     *         {@code TIMEOUT}, or {@code EMPTY}
     */
    ResolveResult resolve(SpawnContext ctx);
}

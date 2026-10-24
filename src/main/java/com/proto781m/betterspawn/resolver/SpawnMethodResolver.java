package com.proto781m.betterspawn.resolver;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.api.SpawnMethodRegistry;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.template.SpawnRule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spawn-method dispatcher and fallback chain.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Call the implementation named by {@code rule.method()};</li>
 *   <li>Measure elapsed time and catch exceptions from implementations so one failure
 *       does not abort the whole resolution;</li>
 *   <li>When the main method fails, recurse through {@code rule.fallback} with a depth
 *       limit and cycle protection.</li>
 * </ol>
 *
 * <p>The dispatcher does not persist anything; the caller decides whether to store the result.
 */
public final class SpawnMethodResolver {

    public static final SpawnMethodResolver INSTANCE = new SpawnMethodResolver();

    private SpawnMethodResolver() {
    }

    public ResolveResult resolve(SpawnContext ctx) {
        return resolve(ctx, 0, new HashSet<>());
    }

    private ResolveResult resolve(SpawnContext ctx, int depth, Set<String> visited) {
        SpawnRule rule = ctx.rule();
        String id = rule.method();
        if (!visited.add(id)) {
            return ResolveResult.empty(id, ResolveResult.Status.EMPTY);
        }

        SpawnMethod method = SpawnMethodRegistry.get(id);
        if (method == null) {
            BetterSpawn.LOGGER.warn("Unknown spawn method '{}' for {}; trying fallback", id, rule.dimension());
            return attemptFallback(ctx, id, depth, visited);
        }

        long start = System.nanoTime();
        ResolveResult result;
        try {
            result = method.resolve(ctx);
        } catch (Exception ex) {
            BetterSpawn.LOGGER.error("Spawn method '{}' failed in dimension {}", id, rule.dimension(), ex);
            result = ResolveResult.empty(id, ResolveResult.Status.EMPTY);
        }
        result = withElapsed(result, (System.nanoTime() - start) / 1_000_000L);

        if (result.status() == ResolveResult.Status.OK || result.status() == ResolveResult.Status.FALLBACK) {
            return result;
        }
        return attemptFallback(ctx, id, depth, visited);
    }

    private ResolveResult attemptFallback(SpawnContext ctx, String currentMethod, int depth, Set<String> visited) {
        if (depth >= BetterSpawnConfig.FALLBACK_MAX_DEPTH.get()) {
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }

        String fallbackId = ctx.rule().fallback();
        if (fallbackId == null || fallbackId.isBlank()) {
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }
        if (SpawnMethodRegistry.get(fallbackId) == null) {
            BetterSpawn.LOGGER.warn("Unknown fallback method '{}' for {}", fallbackId, ctx.rule().dimension());
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }

        // Fallbacks use fallback_parameters; the fallback field is retained to support multi-level fallback.
        SpawnRule fallbackRule = new SpawnRule(
                ctx.rule().dimension(),
                fallbackId,
                ctx.rule().fallbackParameters(),
                ctx.rule().fallback(),
                ctx.rule().fallbackParameters());
        SpawnContext fallbackCtx = new SpawnContext(
                ctx.level(), fallbackRule, ctx.seed(), ctx.simulate(),
                ctx.deadlineMillis(), ctx.trace());

        ResolveResult result = resolve(fallbackCtx, depth + 1, visited);
        if (result.status() == ResolveResult.Status.OK || result.status() == ResolveResult.Status.FALLBACK) {
            return new ResolveResult(
                    result.pos(), result.dimension(), result.angle(), result.usedMethod(), true,
                    result.elapsedMillis(), result.samples(), result.maxRadiusReached(),
                    ResolveResult.Status.FALLBACK, result.warnings());
        }

        // The fallback also failed: return a positionless failure so callers do not treat an UNSAFE position as success.
        return new ResolveResult(null, ctx.rule().dimensionKey(), 0.0F, currentMethod,
                false, result.elapsedMillis(), result.samples(), result.maxRadiusReached(),
                result.status() == ResolveResult.Status.TIMEOUT
                        ? ResolveResult.Status.TIMEOUT
                        : ResolveResult.Status.EMPTY,
                result.warnings() == null ? List.of() : result.warnings());
    }

    private static ResolveResult withElapsed(ResolveResult result, long elapsed) {
        return new ResolveResult(
                result.pos(), result.dimension(), result.angle(), result.usedMethod(),
                result.fallbackUsed(), elapsed, result.samples(), result.maxRadiusReached(),
                result.status(), result.warnings() == null ? List.of() : result.warnings());
    }
}

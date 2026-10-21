package com.proto781m.betterspawn.resolver;

import java.util.HashSet;
import java.util.Set;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.api.SpawnMethodRegistry;
import com.proto781m.betterspawn.template.SpawnRule;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 出生方法调度器 + fallback 链。
 *
 * <p>职责：
 * <ol>
 *   <li>从上下文中取出 {@code method}，查注册表调用对应实现；</li>
 *   <li>对结果做计时；</li>
 *   <li>若主方法失败（非 OK），按 {@code rule.fallback} 递归调用，带深度上限与已访问集合防环。</li>
 * </ol>
 *
 * <p>调度器本身不落库；由调用方（首次生成 / {@code /gotodim}）决定是否持久化。
 */
public final class SpawnMethodResolver {

    public static final SpawnMethodResolver INSTANCE = new SpawnMethodResolver();

    private SpawnMethodResolver() {
    }

    public ResolveResult resolve(SpawnContext ctx) {
        return resolve(ctx, 0, new HashSet<>());
    }

    private ResolveResult resolve(SpawnContext ctx, int depth, Set<String> visited) {
        String id = ctx.rule().method();
        if (!visited.add(id)) {
            return ResolveResult.empty(id, ResolveResult.Status.EMPTY);
        }

        SpawnMethod method = SpawnMethodRegistry.get(id);
        if (method == null) {
            return attemptFallback(ctx, "unknown:" + id, depth, visited);
        }

        long start = System.nanoTime();
        ResolveResult result = method.resolve(ctx);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        result = withElapsed(result, elapsed);

        if (result.status() == ResolveResult.Status.OK) {
            return result;
        }
        return attemptFallback(ctx, result.usedMethod(), depth, visited);
    }

    private ResolveResult attemptFallback(SpawnContext ctx, String currentMethod, int depth, Set<String> visited) {
        if (depth >= BetterSpawnConfig.FALLBACK_MAX_DEPTH.get()) {
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }
        String fallbackId = ctx.rule().fallback();
        if (fallbackId == null || fallbackId.isBlank()) {
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }
        SpawnMethod fallback = SpawnMethodRegistry.get(fallbackId);
        if (fallback == null) {
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }

        // 构造 fallback 规则：主方法换成 fallback，参数换成 fallback_parameters
        SpawnRule fallbackRule = new SpawnRule(
                ctx.rule().dimensionKey().location(), fallbackId, ctx.rule().fallbackParameters(),
                ctx.rule().fallback(), ctx.rule().fallbackParameters());
        SpawnContext fallbackCtx = new SpawnContext(
                ctx.level(), fallbackRule, ctx.seed(), ctx.simulate(), ctx.deadlineMillis(), ctx.trace());

        ResolveResult result = resolve(fallbackCtx, depth + 1, visited);
        if (result.status() == ResolveResult.Status.OK) {
            return new ResolveResult(
                    result.pos(), result.dimension(), result.angle(), result.usedMethod(), true,
                    result.elapsedMillis(), result.samples(), result.maxRadiusReached(),
                    ResolveResult.Status.FALLBACK, result.warnings());
        }
        return result;
    }

    private static ResolveResult withElapsed(ResolveResult r, long elapsed) {
        return new ResolveResult(r.pos(), r.dimension(), r.angle(), r.usedMethod(), r.fallbackUsed(), elapsed,
                r.samples(), r.maxRadiusReached(), r.status(), r.warnings());
    }
}

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
 * 出生方法调度器 + fallback 链。
 *
 * <p>职责：
 * <ol>
 *   <li>按 {@code rule.method()} 调用具体实现；</li>
 *   <li>记录耗时，捕获实现抛出的异常（避免一个方法出错拖垮整个解析）；</li>
 *   <li>主方法失败时按 {@code rule.fallback} 递归调用，并限制深度、防止成环。</li>
 * </ol>
 *
 * <p>调度器本身不落库；由调用方决定是否持久化。
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
            BetterSpawn.LOGGER.warn("未知的出生点方法 '{}'（维度 {}），尝试 fallback", id, rule.dimension());
            return attemptFallback(ctx, id, depth, visited);
        }

        long start = System.nanoTime();
        ResolveResult result;
        try {
            result = method.resolve(ctx);
        } catch (Exception ex) {
            BetterSpawn.LOGGER.error("出生点方法 '{}' 在维度 {} 执行失败", id, rule.dimension(), ex);
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
            BetterSpawn.LOGGER.warn("未知的 fallback 方法 '{}'（维度 {}）", fallbackId, ctx.rule().dimension());
            return ResolveResult.empty(currentMethod, ResolveResult.Status.EMPTY);
        }

        // fallback 使用 fallback_parameters；fallback 字段继续保留，以支持多级回退
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

        // fallback 也失败：统一返回“无坐标”的失败结果，避免调用方把 UNSAFE 的 pos 当成成功
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

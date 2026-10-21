package com.proto781m.betterspawn.method;

import java.util.List;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.ChunkSearchHelper;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 定点坐标方法（末地）：直接使用 JSON 指定的 x, y, z 坐标。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code x}, {@code y}, {@code z}：目标坐标（必填）</li>
 *   <li>{@code angle}：朝向角度（可选，默认随机）</li>
 *   <li>{@code safe_check}：是否做安全校验，不通过则走 fallback（默认 true）</li>
 * </ul>
 */
public class FixedSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "fixed";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        RandomSource rng = RandomSource.create(ctx.seed());

        int x = ctx.rule().getInt("x", 0);
        int y = ctx.rule().getInt("y", 64);
        int z = ctx.rule().getInt("z", 0);
        float angle = (float) ctx.rule().getDouble("angle", rng.nextFloat() * 360.0);
        boolean safeCheck = ctx.rule().getBool("safe_check", true);

        BlockPos pos = new BlockPos(x, y, z);

        if (!ChunkSearchHelper.ensureChunk(level, pos)) {
            return ResolveResult.empty(id(), ResolveResult.Status.UNSAFE);
        }

        if (safeCheck && !SpawnSafetyValidator.isSafe(level, pos)) {
            List<String> warnings = SpawnSafetyValidator.validate(level, pos);
            return new ResolveResult(pos, ctx.rule().dimensionKey(), angle, id(), false, 0, 1, 0,
                    ResolveResult.Status.UNSAFE, warnings);
        }

        List<String> warnings = safeCheck ? SpawnSafetyValidator.validate(level, pos) : List.of();
        return new ResolveResult(pos, ctx.rule().dimensionKey(), angle, id(), false, 0, 1, 0,
                ResolveResult.Status.OK, warnings);
    }
}